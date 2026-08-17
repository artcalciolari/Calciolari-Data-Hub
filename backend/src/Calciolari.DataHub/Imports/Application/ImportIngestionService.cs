using System.Security.Cryptography;
using System.Text.RegularExpressions;
using Calciolari.DataHub.Imports.Domain.Hints;
using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;
using Calciolari.DataHub.Imports.Infrastructure.Storage;
using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Persistence.Entities;
using Calciolari.DataHub.Shared.Api;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Npgsql;

namespace Calciolari.DataHub.Imports.Application;

/// <summary>
/// Ingests one QRP upload: hash → immutable raw store → dedup → parse → optional publish.
/// HTTP accepts files and enqueues parse; <see cref="Ingest"/> stays synchronous for tests.
/// </summary>
public sealed class ImportIngestionService : IImportFileProcessor
{
    private const string Source = "INTERPDV";
    private static readonly Regex Kv = new(@"(\w+)=([^\s]+)", RegexOptions.Compiled);
    private static readonly ConcurrentLockMap ReprocessLocks = new();

    private readonly IRawFileStorage _rawFileStorage;
    private readonly IImportParser _parser;
    private readonly FilenameHintsParser _hintsParser;
    private readonly DataHubDbContext _db;
    private readonly ILogger<ImportIngestionService> _logger;
    private readonly IImportWorkQueue? _workQueue;
    private readonly ImportMetrics? _metrics;

    public ImportIngestionService(
        IRawFileStorage rawFileStorage,
        IImportParser parser,
        FilenameHintsParser hintsParser,
        DataHubDbContext db,
        ILogger<ImportIngestionService>? logger = null,
        IImportWorkQueue? workQueue = null,
        ImportMetrics? metrics = null)
    {
        _rawFileStorage = rawFileStorage;
        _parser = parser;
        _hintsParser = hintsParser;
        _db = db;
        _logger = logger ?? NullLogger<ImportIngestionService>.Instance;
        _workQueue = workQueue;
        _metrics = metrics;
    }

    public ImportedFileResult Ingest(Stream content, string? originalFilename)
    {
        var jobId = CreateJob();
        var result = IngestIntoJob(jobId, content, originalFilename);
        CompleteJob(jobId);
        var job = _db.ImportJobs.AsNoTracking().Single(j => j.Id == jobId);
        return result with { JobStatus = job.Status };
    }

    public Guid CreateJob()
    {
        return InTransaction(() =>
        {
            var job = new ImportJobEntity(Guid.NewGuid(), "PROCESSING");
            _db.ImportJobs.Add(job);
            _db.SaveChanges();
            return job.Id;
        });
    }

    public void CompleteJob(Guid jobId)
    {
        InTransaction(() =>
        {
            var job = _db.ImportJobs.Single(j => j.Id == jobId);
            if (job.CompletedAt is not null)
            {
                return;
            }

            var files = _db.ImportFiles.Where(f => f.ImportJobId == jobId).OrderBy(f => f.CreatedAt).ToList();
            if (files.Any(f => f.Status is "PENDING" or "PROCESSING"))
            {
                return;
            }

            var imported = files.Count(f => f.Status is "IMPORTED" or "WARNING");
            var failed = files.Count(f => f.Status is "INVALID" or "FAILED");
            if (files.Count == 0 || (failed > 0 && imported == 0))
            {
                job.Status = "FAILED";
            }
            else if (failed > 0)
            {
                job.Status = "PARTIAL_SUCCESS";
            }
            else
            {
                job.Status = "SUCCEEDED";
            }

            job.CompletedAt = DateTimeOffset.UtcNow;
            _db.SaveChanges();
            _metrics?.RecordJobCompleted(job.Status);
        });
    }

    public ReprocessResult Reprocess(Guid importFileId)
    {
        var file = _db.ImportFiles.AsNoTracking().SingleOrDefault(f => f.Id == importFileId)
                   ?? throw new ApiException(StatusCodes.Status404NotFound, "Import file not found");
        var artifact = _db.RawArtifacts.AsNoTracking().Single(a => a.Id == file.RawArtifactId);

        lock (ReprocessLocks.For(artifact.Id))
        {
            return ReprocessLocked(file.Id, artifact.Id);
        }
    }

    private ReprocessResult ReprocessLocked(Guid importFileId, Guid artifactId)
    {
        var file = _db.ImportFiles.AsNoTracking().Single(f => f.Id == importFileId);
        var artifact = _db.RawArtifacts.AsNoTracking().Single(a => a.Id == artifactId);

        try
        {
            using var ignored = _rawFileStorage.OpenVerified(artifact.StorageKey, artifact.Sha256, artifact.ByteSize);
        }
        catch (RawStorageIntegrityException ex)
        {
            throw new ApiException(StatusCodes.Status409Conflict, ex.Message, ex);
        }

        var leaseOwner = "reprocess-" + Guid.NewGuid();
        var claim = InTransaction(() => ClaimReprocess(artifact.Id, leaseOwner));

        ParsedImport parsed;
        try
        {
            using var input = _rawFileStorage.OpenVerified(artifact.StorageKey, artifact.Sha256, artifact.ByteSize);
            parsed = _parser.Parse(new ParserInput(input, artifact.ByteSize, file.OriginalFilename, artifact.DetectedType));
        }
        catch (RawStorageIntegrityException ex)
        {
            InTransaction(() => FailAttempt(claim.AttemptId, "raw integrity failed: " + ex.Message));
            throw new ApiException(StatusCodes.Status409Conflict, ex.Message, ex);
        }
        catch (IOException ex)
        {
            InTransaction(() => FailAttempt(claim.AttemptId, "read failed: " + ex.Message));
            throw;
        }
        catch (Exception ex) when (ex is not ApiException)
        {
            InTransaction(() => FailAttempt(claim.AttemptId, ex.Message));
            throw;
        }

        return InTransaction(() => FinalizeReprocess(importFileId, claim, leaseOwner, parsed));
    }

    private ReprocessClaim ClaimReprocess(Guid artifactId, string leaseOwner)
    {
        var locked = FindArtifactForUpdate(artifactId)!;

        var latest = LatestAttempt(locked.Id);
        if (HasActiveLease(latest))
        {
            throw new ApiException(StatusCodes.Status409Conflict, "parse attempt already in progress for artifact");
        }

        Guid? previousActive = _db.ArtifactPublications
            .Where(p => p.RawArtifactId == locked.Id)
            .Select(p => (Guid?)p.ActiveParseAttemptId)
            .SingleOrDefault();

        var nextCount = latest is null ? 1 : latest.AttemptCount + 1;
        var attempt = new ParseAttemptEntity(
            Guid.NewGuid(),
            locked.Id,
            InterPdvQrpParser.ParserName,
            InterPdvQrpParser.ParserVersion,
            "PROCESSING",
            nextCount)
        {
            StartedAt = DateTimeOffset.UtcNow,
            LeaseOwner = leaseOwner,
            LeaseGeneration = 1,
            LeaseUntil = DateTimeOffset.UtcNow.AddSeconds(300)
        };
        _db.ParseAttempts.Add(attempt);
        _db.SaveChanges();
        return new ReprocessClaim(attempt.Id, locked.Id, previousActive);
    }

    private ReprocessResult FinalizeReprocess(
        Guid importFileId,
        ReprocessClaim claim,
        string leaseOwner,
        ParsedImport parsed)
    {
        _ = FindArtifactForUpdate(claim.ArtifactId)!;
        var attempt = _db.ParseAttempts.Single(a => a.Id == claim.AttemptId);
        if (!string.Equals(leaseOwner, attempt.LeaseOwner, StringComparison.Ordinal))
        {
            throw new ApiException(StatusCodes.Status409Conflict, "reprocess lease lost");
        }

        var file = _db.ImportFiles.Single(f => f.Id == importFileId);
        PersistParseRows(attempt.Id, parsed);
        var outcome = ApplyParseOutcome(claim.ArtifactId, attempt.Id, parsed);

        attempt.Status = outcome.ParseStatus;
        attempt.RecordsFound = parsed.Movements.Count;
        attempt.CompletedAt = DateTimeOffset.UtcNow;
        attempt.LeaseUntil = null;
        file.ParseAttemptId = attempt.Id;
        file.Status = outcome.Published ? "IMPORTED" : MapFileStatus(outcome.ParseStatus);
        file.CompletedAt = DateTimeOffset.UtcNow;
        _db.SaveChanges();
        _logger.LogInformation(
            "Reprocessed originalFilename={OriginalFilename} sha256={Sha256} parser={Parser} records={Records} parseStatus={ParseStatus} published={Published}",
            file.OriginalFilename,
            _db.RawArtifacts.AsNoTracking().Single(a => a.Id == claim.ArtifactId).Sha256,
            InterPdvQrpParser.ParserName,
            parsed.Movements.Count,
            outcome.ParseStatus,
            outcome.Published);
        return new ReprocessResult(
            file.Id,
            claim.ArtifactId,
            claim.PreviousActiveParseAttemptId,
            attempt.Id,
            outcome.Published,
            outcome.ParseStatus,
            file.Status,
            parsed.Movements.Count);
    }

    internal void FailAttempt(Guid attemptId, string? summary)
    {
        var attempt = _db.ParseAttempts.SingleOrDefault(a => a.Id == attemptId);
        if (attempt is null)
        {
            return;
        }

        attempt.Status = "FAILED";
        attempt.ErrorSummary = summary is null ? null : summary[..Math.Min(summary.Length, 500)];
        attempt.CompletedAt = DateTimeOffset.UtcNow;
        attempt.LeaseUntil = null;
        _db.SaveChanges();
        _logger.LogWarning("Parse attempt {AttemptId} failed: {Summary}", attemptId, attempt.ErrorSummary);
    }

    public ImportedFileResult AcceptIntoJob(Guid jobId, Stream content, string? originalFilename)
    {
        var ctx = AcceptCore(jobId, content, originalFilename, enqueue: true);
        return ToResult(ctx, null, ctx.AlreadyPublished);
    }

    public ImportedFileResult IngestIntoJob(Guid jobId, Stream content, string? originalFilename)
    {
        var ctx = AcceptCore(jobId, content, originalFilename, enqueue: false);
        if (!ctx.SkipParse && ctx.File.Status == "PROCESSING")
        {
            return ProcessAcceptedFile(ctx.File.Id);
        }

        return ToResult(ctx, null, ctx.AlreadyPublished);
    }

    public ImportedFileResult ProcessAcceptedFile(Guid importFileId)
    {
        var lookup = _db.ImportFiles.AsNoTracking().SingleOrDefault(f => f.Id == importFileId)
                     ?? throw new InvalidOperationException("unknown import file " + importFileId);
        lock (ReprocessLocks.For(lookup.RawArtifactId))
        {
            return ProcessAcceptedFileLocked(importFileId);
        }
    }

    public void ReclaimExpiredProcessingLeases()
    {
        var now = DateTimeOffset.UtcNow;
        var expired = _db.ParseAttempts
            .Where(a => a.Status == "PROCESSING" && a.LeaseUntil != null && a.LeaseUntil < now)
            .ToList();
        foreach (var attempt in expired)
        {
            attempt.LeaseUntil = now.AddSeconds(300);
            attempt.LeaseGeneration += 1;
            _db.SaveChanges();
            var files = _db.ImportFiles
                .Where(f => f.ParseAttemptId == attempt.Id && f.Status == "PROCESSING")
                .Select(f => f.Id)
                .ToList();
            foreach (var fileId in files)
            {
                if (_workQueue is not null)
                {
                    _workQueue.Enqueue(fileId);
                }
                else
                {
                    ProcessAcceptedFile(fileId);
                }
            }
        }
    }

    private IngestContext AcceptCore(Guid jobId, Stream content, string? originalFilename, bool enqueue)
    {
        ArgumentNullException.ThrowIfNull(content);
        var filename = originalFilename ?? string.Empty;
        var spool = SpoolAndHash(content);
        try
        {
            StoredRawFile stored;
            using (var storedStream = File.OpenRead(spool.TempPath))
            {
                stored = _rawFileStorage.PutIfAbsent(
                    storedStream,
                    RawFileDescriptor.Create(spool.Sha256, spool.ByteSize, "QRP"));
            }

            if (stored.Created)
            {
                _metrics?.AddRawBytes(spool.ByteSize);
            }

            var hints = _hintsParser.Parse(filename);
            var hintsJson = FilenameHintsJson.Write(hints);

            IngestContext? ctx = null;
            Exception? lastConflict = null;
            for (var attempt = 0; attempt < 5; attempt++)
            {
                try
                {
                    ctx = InTransaction(() => OpenOrCreateOnce(jobId, spool, stored, filename, hintsJson));
                    break;
                }
                catch (DbUpdateException ex) when (IsUniqueViolation(ex))
                {
                    lastConflict = ex;
                    _db.ChangeTracker.Clear();
                }
            }

            if (ctx is null)
            {
                throw new InvalidOperationException(
                    "failed to open/create artifact after retries for " + spool.Sha256, lastConflict);
            }

            if (enqueue && !ctx.SkipParse && ctx.File.Status == "PROCESSING")
            {
                _workQueue?.Enqueue(ctx.File.Id);
            }

            if (ctx.SkipParse)
            {
                _logger.LogInformation(
                    "Import skipped parse originalFilename={OriginalFilename} sha256={Sha256} parser={Parser} fileStatus={FileStatus} deduplicated={Deduplicated}",
                    filename,
                    spool.Sha256,
                    InterPdvQrpParser.ParserName,
                    ctx.File.Status,
                    ctx.File.Deduplicated);
                if (ShouldRecordSkippedFileMetrics(ctx.File.Status))
                {
                    _metrics?.RecordFile(ctx.File.Status, ctx.File.Deduplicated, 0);
                }
            }

            return ctx;
        }
        finally
        {
            if (File.Exists(spool.TempPath))
            {
                File.Delete(spool.TempPath);
            }
        }
    }

    private ImportedFileResult ProcessAcceptedFileLocked(Guid importFileId)
    {
        var file = _db.ImportFiles.Single(f => f.Id == importFileId);
        var job = _db.ImportJobs.Single(j => j.Id == file.ImportJobId);
        var artifact = _db.RawArtifacts.Single(a => a.Id == file.RawArtifactId);
        if (file.ParseAttemptId is null)
        {
            throw new InvalidOperationException("import file has no parse attempt " + importFileId);
        }

        var attempt = _db.ParseAttempts.Single(a => a.Id == file.ParseAttemptId);
        if (file.Status is not "PENDING" and not "PROCESSING")
        {
            CompleteJob(job.Id);
            return ToResult(new IngestContext(job, file, artifact, attempt, true, AlreadyPublished(artifact.Id)), null, AlreadyPublished(artifact.Id));
        }

        if (attempt.Status is not "PENDING" and not "PROCESSING")
        {
            var published = AlreadyPublished(artifact.Id);
            file.Status = published ? "IMPORTED" : MapFileStatus(attempt.Status);
            _metrics?.RecordFile(file.Status, file.Deduplicated, ElapsedMs(file));
            file.CompletedAt = DateTimeOffset.UtcNow;
            _db.SaveChanges();
            SyncLinkedFiles(attempt.Id, file.Id);
            CompleteJob(job.Id);
            return ToResult(new IngestContext(job, file, artifact, attempt, true, published), null, published);
        }

        ParsedImport parsed;
        using (var input = _rawFileStorage.OpenVerified(artifact.StorageKey, artifact.Sha256, artifact.ByteSize))
        {
            parsed = _parser.Parse(new ParserInput(input, artifact.ByteSize, file.OriginalFilename, "QRP"));
        }

        var ctx = new IngestContext(job, file, artifact, attempt, false, false);
        var result = InTransaction(() => FinalizeParse(ctx, parsed));
        _logger.LogInformation(
            "Imported originalFilename={OriginalFilename} sha256={Sha256} parser={Parser} records={Records} parseStatus={ParseStatus} published={Published} deduplicated={Deduplicated}",
            file.OriginalFilename,
            artifact.Sha256,
            InterPdvQrpParser.ParserName,
            result.RecordsFound,
            result.ParseStatus,
            result.Published,
            result.Deduplicated);
        _metrics?.RecordFile(result.FileStatus, result.Deduplicated, ElapsedMs(file));
        SyncLinkedFiles(attempt.Id, file.Id);
        CompleteJob(job.Id);
        return result;
    }

    private void SyncLinkedFiles(Guid parseAttemptId, Guid sourceFileId)
    {
        var source = _db.ImportFiles.Single(f => f.Id == sourceFileId);
        var linked = _db.ImportFiles
            .Where(f => f.ParseAttemptId == parseAttemptId && f.Id != sourceFileId && f.Status == "PROCESSING")
            .ToList();
        if (linked.Count == 0)
        {
            return;
        }

        foreach (var file in linked)
        {
            file.Status = source.Status;
            file.CompletedAt = source.CompletedAt;
            _metrics?.RecordFile(file.Status, file.Deduplicated, ElapsedMs(file));
        }

        _db.SaveChanges();
        foreach (var file in linked)
        {
            CompleteJob(file.ImportJobId);
        }
    }

    private bool AlreadyPublished(Guid artifactId) =>
        _db.ArtifactPublications.Any(p => p.RawArtifactId == artifactId);

    private static long ElapsedMs(ImportFileEntity file)
    {
        var end = file.CompletedAt ?? DateTimeOffset.UtcNow;
        var ms = (long)(end - file.CreatedAt).TotalMilliseconds;
        return Math.Max(0L, ms);
    }

    private IngestContext OpenOrCreateOnce(
        Guid jobId,
        SpoolResult spool,
        StoredRawFile stored,
        string filename,
        string hintsJson)
    {
        var job = _db.ImportJobs.SingleOrDefault(j => j.Id == jobId)
                  ?? throw new ArgumentException("unknown job " + jobId);

        var existing = _db.RawArtifacts.SingleOrDefault(a => a.Sha256 == spool.Sha256);
        RawArtifactEntity artifact;
        if (existing is null)
        {
            artifact = Add(new RawArtifactEntity(
                Guid.NewGuid(), spool.Sha256, spool.ByteSize, stored.StorageKey, "QRP"));
            _db.SaveChanges();
        }
        else
        {
            artifact = existing;
        }

        var file = new ImportFileEntity(
            Guid.NewGuid(), job.Id, artifact.Id, filename, Source, "PENDING")
        {
            FilenameHints = hintsJson
        };

        var latest = LatestAttempt(artifact.Id);
        if (latest is not null)
        {
            var published = _db.ArtifactPublications.Any(p => p.RawArtifactId == artifact.Id);
            file.ParseAttemptId = latest.Id;
            file.Deduplicated = true;
            var original = _db.ImportFiles
                .Where(f => f.RawArtifactId == artifact.Id && !f.Deduplicated)
                .OrderBy(f => f.CreatedAt)
                .FirstOrDefault();
            if (original is not null)
            {
                file.DuplicateOfImportFileId = original.Id;
            }

            if (latest.Status == "PENDING")
            {
                return SkipInFlight(job, file, artifact, latest, published);
            }

            if (latest.Status == "PROCESSING")
            {
                return SkipInFlight(job, file, artifact, latest, published);
            }

            if (latest.Status is "VALID" or "WARNING")
            {
                file.Status = published ? "IMPORTED" : MapFileStatus(latest.Status);
                file.CompletedAt = DateTimeOffset.UtcNow;
                _db.ImportFiles.Add(file);
                _db.SaveChanges();
                return new IngestContext(job, file, artifact, latest, true, published);
            }

            if (latest.Status == "INVALID")
            {
                file.Status = "INVALID";
                file.CompletedAt = DateTimeOffset.UtcNow;
                _db.ImportFiles.Add(file);
                _db.SaveChanges();
                return new IngestContext(job, file, artifact, latest, true, false);
            }
        }

        var nextCount = latest is null ? 1 : latest.AttemptCount + 1;
        var attempt = new ParseAttemptEntity(
            Guid.NewGuid(),
            artifact.Id,
            InterPdvQrpParser.ParserName,
            InterPdvQrpParser.ParserVersion,
            "PROCESSING",
            nextCount)
        {
            StartedAt = DateTimeOffset.UtcNow,
            LeaseOwner = "ingest-" + Guid.NewGuid(),
            LeaseGeneration = 1,
            LeaseUntil = DateTimeOffset.UtcNow.AddSeconds(300)
        };
        _db.ParseAttempts.Add(attempt);

        file.ParseAttemptId = attempt.Id;
        file.Status = "PROCESSING";
        file.Deduplicated = existing is not null;
        if (existing is not null)
        {
            var original = _db.ImportFiles
                .Where(f => f.RawArtifactId == artifact.Id && !f.Deduplicated)
                .OrderBy(f => f.CreatedAt)
                .FirstOrDefault();
            if (original is not null)
            {
                file.DuplicateOfImportFileId = original.Id;
            }
        }

        _db.ImportFiles.Add(file);
        _db.SaveChanges();
        return new IngestContext(job, file, artifact, attempt, false, false);
    }

    private IngestContext SkipInFlight(
        ImportJobEntity job,
        ImportFileEntity file,
        RawArtifactEntity artifact,
        ParseAttemptEntity latest,
        bool published)
    {
        file.Status = "PROCESSING";
        _db.ImportFiles.Add(file);
        _db.SaveChanges();
        return new IngestContext(job, file, artifact, latest, true, published);
    }

    private ImportedFileResult FinalizeParse(IngestContext ctx, ParsedImport parsed)
    {
        var attempt = _db.ParseAttempts.Single(a => a.Id == ctx.Attempt.Id);
        var file = _db.ImportFiles.Single(f => f.Id == ctx.File.Id);
        var job = _db.ImportJobs.Single(j => j.Id == ctx.Job.Id);

        PersistParseRows(attempt.Id, parsed);
        var outcome = ApplyParseOutcome(ctx.Artifact.Id, attempt.Id, parsed);

        attempt.Status = outcome.ParseStatus;
        attempt.RecordsFound = parsed.Movements.Count;
        attempt.CompletedAt = DateTimeOffset.UtcNow;
        attempt.LeaseUntil = null;
        file.Status = outcome.Published ? "IMPORTED" : MapFileStatus(outcome.ParseStatus);
        file.CompletedAt = DateTimeOffset.UtcNow;
        _db.SaveChanges();
        return ToResult(new IngestContext(job, file, ctx.Artifact, attempt, ctx.SkipParse, outcome.Published), parsed, outcome.Published);
    }

    private (bool Published, string ParseStatus) ApplyParseOutcome(Guid artifactId, Guid attemptId, ParsedImport parsed)
    {
        if (parsed.HasFatalOrError())
        {
            var failed = parsed.Issues.Any(i => i.Severity == IssueSeverity.Fatal) ? "FAILED" : "INVALID";
            return (false, failed);
        }

        var saleIds = parsed.Movements
            .Where(m => m.Direction == MovementDirection.Out && m.ExternalSaleId is not null)
            .Select(m => m.ExternalSaleId!)
            .Distinct()
            .ToList();
        var overlap = saleIds.Count > 0 && ExistsOverlappingPublishedSales(artifactId, saleIds);
        if (overlap)
        {
            _db.ValidationResults.Add(new ValidationResultEntity(
                Guid.NewGuid(),
                attemptId,
                "OVERLAPPING_REPORT",
                "WARNING",
                null,
                null,
                null,
                null,
                "identity-v1",
                "canonical publication blocked"));
            return (false, "WARNING");
        }

        if (parsed.ExternalProductId is null)
        {
            var status = parsed.Issues.Any(i => i.Severity == IssueSeverity.Warning) ? "WARNING" : "VALID";
            return (false, status);
        }

        PublishCanonical(artifactId, attemptId, parsed);
        var parseStatus = parsed.Issues.Any(i => i.Severity == IssueSeverity.Warning) ? "WARNING" : "VALID";
        return (true, parseStatus);
    }

    private void PersistParseRows(Guid attemptId, ParsedImport parsed)
    {
        foreach (var movement in parsed.Movements)
        {
            _db.ParsedMovements.Add(ParsedMovementEntity.From(Guid.NewGuid(), attemptId, movement));
        }

        foreach (var issue in parsed.Issues)
        {
            if (issue.Stage == IssueStage.Validation)
            {
                _db.ValidationResults.Add(ToValidationEntity(attemptId, issue));
            }
        }
    }

    private void PublishCanonical(Guid artifactId, Guid attemptId, ParsedImport parsed)
    {
        var externalProductId = parsed.ExternalProductId!;
        var product = _db.Products.SingleOrDefault(p => p.ExternalSource == Source && p.ExternalId == externalProductId)
                      ?? Add(new ProductEntity(
                          Guid.NewGuid(),
                          Source,
                          externalProductId,
                          parsed.ProductName ?? externalProductId,
                          attemptId));
        if (parsed.ProductName is not null && parsed.ProductName != product.Name)
        {
            product.SetName(parsed.ProductName);
        }

        var saleIds = parsed.Movements
            .Where(m => m.Direction == MovementDirection.Out && m.ExternalSaleId is not null)
            .Select(m => m.ExternalSaleId!)
            .Distinct()
            .ToList();
        var salesByExternal = saleIds.Count == 0
            ? new Dictionary<string, SaleEntity>()
            : _db.Sales
                .Where(s => s.ExternalSource == Source && saleIds.Contains(s.ExternalSaleId))
                .ToDictionary(s => s.ExternalSaleId, s => s);

        foreach (var movement in parsed.Movements)
        {
            if (movement.Direction != MovementDirection.Out)
            {
                continue;
            }

            if (movement.ExternalSaleId is null || movement.Quantity is null
                || movement.UnitPrice is null || movement.Total is null)
            {
                continue;
            }

            if (!salesByExternal.TryGetValue(movement.ExternalSaleId, out var sale))
            {
                sale = Add(new SaleEntity(
                    Guid.NewGuid(),
                    Source,
                    movement.ExternalSaleId,
                    movement.OccurredAt,
                    attemptId));
                salesByExternal[movement.ExternalSaleId] = sale;
            }
            _db.SaleItems.Add(new SaleItemEntity(
                Guid.NewGuid(),
                sale.Id,
                product.Id,
                attemptId,
                movement.SourceRecordIndex,
                movement.Quantity.Value,
                movement.UnitPrice.Value,
                movement.DiscountPercentage,
                movement.Total.Value,
                movement.PreviousStock,
                movement.ResultingStock));
        }

        var publication = _db.ArtifactPublications.SingleOrDefault(p => p.RawArtifactId == artifactId)
                          ?? Add(new ArtifactPublicationEntity(artifactId, attemptId));
        publication.ActiveParseAttemptId = attemptId;
        publication.PublishedAt = DateTimeOffset.UtcNow;
        _db.SaveChanges();
    }

    internal static ValidationResultEntity ToValidationEntity(Guid attemptId, ParseIssue issue)
    {
        var status = issue.Severity switch
        {
            IssueSeverity.Info => "VALID",
            IssueSeverity.Warning => "WARNING",
            IssueSeverity.Error or IssueSeverity.Fatal => "INVALID",
            _ => "INVALID"
        };
        decimal? source = null;
        decimal? calculated = null;
        decimal? difference = null;
        decimal? tolerance = null;
        var ruleVersion = InterPdvQrpParser.ParserVersion;
        foreach (Match match in Kv.Matches(issue.Message))
        {
            var key = match.Groups[1].Value;
            var value = match.Groups[2].Value;
            switch (key)
            {
                case "sourceValue":
                    source = decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture);
                    break;
                case "calculatedValue":
                    calculated = decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture);
                    break;
                case "difference":
                    difference = decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture);
                    break;
                case "tolerance":
                    tolerance = decimal.Parse(value, System.Globalization.CultureInfo.InvariantCulture);
                    break;
                case "ruleVersion":
                    ruleVersion = value;
                    break;
            }
        }

        var locator = issue.SourceLocator.ToString();
        return new ValidationResultEntity(
            Guid.NewGuid(),
            attemptId,
            issue.Code,
            status,
            source,
            calculated,
            difference,
            tolerance,
            ruleVersion,
            locator);
    }

    internal static bool HasActiveLease(ParseAttemptEntity? latest)
    {
        if (latest is null)
        {
            return false;
        }

        if (latest.Status != "PENDING" && latest.Status != "PROCESSING")
        {
            return false;
        }

        if (latest.LeaseUntil is null)
        {
            return false;
        }

        return latest.LeaseUntil.Value > DateTimeOffset.UtcNow;
    }

    internal static bool ShouldRecordSkippedFileMetrics(string status) =>
        status is not "PENDING" and not "PROCESSING";

    internal static string MapFileStatus(string parseStatus) => parseStatus switch
    {
        "VALID" => "IMPORTED",
        "WARNING" => "WARNING",
        "INVALID" => "INVALID",
        _ => "FAILED"
    };

    private static ImportedFileResult ToResult(IngestContext ctx, ParsedImport? parsed, bool published) =>
        new(
            ctx.Job.Id,
            ctx.File.Id,
            ctx.Artifact.Id,
            ctx.Attempt.Id,
            ctx.Artifact.Sha256,
            ctx.File.OriginalFilename,
            ctx.File.Deduplicated,
            published,
            ctx.Job.Status,
            ctx.File.Status,
            ctx.Attempt.Status,
            parsed is not null ? parsed.Movements.Count : ctx.Attempt.RecordsFound ?? 0,
            parsed?.Totals.ParsedQuantityTotal,
            parsed?.Totals.ParsedRevenueTotal);

    internal static SpoolResult SpoolAndHash(Stream content)
    {
        var temp = Path.Combine(Path.GetTempPath(), "datahub-upload-" + Guid.NewGuid() + ".qrp");
        try
        {
            byte[] hash;
            long written = 0;
            using (var digest = IncrementalHash.CreateHash(HashAlgorithmName.SHA256))
            using (var output = File.Create(temp))
            {
                var buffer = new byte[8192];
                int read;
                while ((read = content.Read(buffer, 0, buffer.Length)) > 0)
                {
                    output.Write(buffer, 0, read);
                    digest.AppendData(buffer.AsSpan(0, read));
                    written += read;
                }

                hash = digest.GetHashAndReset();
            }

            var sha = Convert.ToHexString(hash).ToLowerInvariant();
            return new SpoolResult(temp, sha, written);
        }
        catch
        {
            if (File.Exists(temp))
            {
                File.Delete(temp);
            }

            throw;
        }
    }

    private ParseAttemptEntity? LatestAttempt(Guid artifactId) =>
        _db.ParseAttempts
            .Where(a => a.RawArtifactId == artifactId
                        && a.ParserName == InterPdvQrpParser.ParserName
                        && a.ParserVersion == InterPdvQrpParser.ParserVersion)
            .OrderByDescending(a => a.AttemptCount)
            .FirstOrDefault();

    private RawArtifactEntity? FindArtifactForUpdate(Guid id) =>
        _db.RawArtifacts
            .FromSql($"SELECT * FROM raw_artifact WHERE id = {id} FOR UPDATE")
            .AsTracking()
            .SingleOrDefault();

    private bool ExistsOverlappingPublishedSales(Guid artifactId, List<string> saleIds) =>
        (from ap in _db.ArtifactPublications
         join pm in _db.ParsedMovements on ap.ActiveParseAttemptId equals pm.ParseAttemptId
         where saleIds.Contains(pm.ExternalSaleId!) && ap.RawArtifactId != artifactId
         select ap).Any();

    private T Add<T>(T entity) where T : class
    {
        _db.Set<T>().Add(entity);
        return entity;
    }

    private T InTransaction<T>(Func<T> action)
    {
        using var tx = _db.Database.BeginTransaction();
        try
        {
            var result = action();
            tx.Commit();
            return result;
        }
        catch
        {
            tx.Rollback();
            _db.ChangeTracker.Clear();
            throw;
        }
    }

    private void InTransaction(Action action) =>
        InTransaction(() =>
        {
            action();
            return 0;
        });

    internal static bool IsUniqueViolation(DbUpdateException ex) =>
        ex.InnerException is PostgresException pg && pg.SqlState == PostgresErrorCodes.UniqueViolation;

    internal sealed record SpoolResult(string TempPath, string Sha256, long ByteSize);

    private sealed record IngestContext(
        ImportJobEntity Job,
        ImportFileEntity File,
        RawArtifactEntity Artifact,
        ParseAttemptEntity Attempt,
        bool SkipParse,
        bool AlreadyPublished);

    private sealed record ReprocessClaim(Guid AttemptId, Guid ArtifactId, Guid? PreviousActiveParseAttemptId);

    private sealed class ConcurrentLockMap
    {
        private readonly System.Collections.Concurrent.ConcurrentDictionary<Guid, object> _locks = new();

        public object For(Guid id) => _locks.GetOrAdd(id, static _ => new object());
    }
}

public sealed record ImportedFileResult(
    Guid JobId,
    Guid ImportFileId,
    Guid RawArtifactId,
    Guid ParseAttemptId,
    string Sha256,
    string OriginalFilename,
    bool Deduplicated,
    bool Published,
    string JobStatus,
    string FileStatus,
    string ParseStatus,
    int RecordsFound,
    decimal? ParsedQuantityTotal,
    decimal? ParsedRevenueTotal);

public sealed record ReprocessResult(
    Guid ImportFileId,
    Guid RawArtifactId,
    Guid? PreviousActiveParseAttemptId,
    Guid ParseAttemptId,
    bool Published,
    string ParseStatus,
    string FileStatus,
    int RecordsFound);
