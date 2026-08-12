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
using Npgsql;

namespace Calciolari.DataHub.Imports.Application;

/// <summary>
/// Ingests one QRP upload: hash → immutable raw store → dedup → parse → optional publish.
/// </summary>
public sealed class ImportIngestionService
{
    private const string Source = "INTERPDV";
    private static readonly Regex Kv = new(@"(\w+)=([^\s]+)", RegexOptions.Compiled);
    private static readonly ConcurrentLockMap ReprocessLocks = new();

    private readonly IRawFileStorage _rawFileStorage;
    private readonly IImportParser _parser;
    private readonly FilenameHintsParser _hintsParser;
    private readonly DataHubDbContext _db;

    public ImportIngestionService(
        IRawFileStorage rawFileStorage,
        IImportParser parser,
        FilenameHintsParser hintsParser,
        DataHubDbContext db)
    {
        _rawFileStorage = rawFileStorage;
        _parser = parser;
        _hintsParser = hintsParser;
        _db = db;
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
            var files = _db.ImportFiles.Where(f => f.ImportJobId == jobId).OrderBy(f => f.CreatedAt).ToList();
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

        var published = false;
        string parseStatus;
        if (parsed.HasFatalOrError())
        {
            parseStatus = parsed.Issues.Any(i => i.Severity == IssueSeverity.Fatal) ? "FAILED" : "INVALID";
        }
        else
        {
            var saleIds = parsed.Movements
                .Where(m => m.Direction == MovementDirection.Out && m.ExternalSaleId is not null)
                .Select(m => m.ExternalSaleId!)
                .Distinct()
                .ToList();
            var overlap = saleIds.Count > 0 && ExistsOverlappingPublishedSales(claim.ArtifactId, saleIds);
            if (overlap)
            {
                parseStatus = "WARNING";
                _db.ValidationResults.Add(new ValidationResultEntity(
                    Guid.NewGuid(),
                    attempt.Id,
                    "OVERLAPPING_REPORT",
                    "WARNING",
                    null,
                    null,
                    null,
                    null,
                    "identity-v1",
                    "canonical publication blocked"));
            }
            else
            {
                PublishCanonical(claim.ArtifactId, attempt.Id, parsed);
                published = true;
                parseStatus = parsed.Issues.Any(i => i.Severity == IssueSeverity.Warning) ? "WARNING" : "VALID";
            }
        }

        attempt.Status = parseStatus;
        attempt.RecordsFound = parsed.Movements.Count;
        attempt.CompletedAt = DateTimeOffset.UtcNow;
        attempt.LeaseUntil = null;
        file.ParseAttemptId = attempt.Id;
        if (published)
        {
            file.Status = MapFileStatus(parseStatus);
        }

        file.CompletedAt = DateTimeOffset.UtcNow;
        _db.SaveChanges();
        return new ReprocessResult(
            file.Id,
            claim.ArtifactId,
            claim.PreviousActiveParseAttemptId,
            attempt.Id,
            published,
            parseStatus,
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
    }

    public ImportedFileResult IngestIntoJob(Guid jobId, Stream content, string? originalFilename)
    {
        ArgumentNullException.ThrowIfNull(content);
        var filename = originalFilename ?? string.Empty;
        var spool = SpoolAndHash(content);

        var stored = _rawFileStorage.PutIfAbsent(
            new MemoryStream(spool.Bytes),
            RawFileDescriptor.Create(spool.Sha256, spool.Bytes.Length, "QRP"));

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

        if (ctx.SkipParse)
        {
            return ToResult(ctx, null, ctx.AlreadyPublished);
        }

        ParsedImport parsed;
        using (var input = _rawFileStorage.OpenVerified(stored.StorageKey, spool.Sha256, spool.Bytes.Length))
        {
            parsed = _parser.Parse(new ParserInput(input, spool.Bytes.Length, filename, "QRP"));
        }

        return InTransaction(() => FinalizeParse(ctx, parsed));
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
                Guid.NewGuid(), spool.Sha256, spool.Bytes.Length, stored.StorageKey, "QRP"));
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

            if (latest.Status is "PENDING" or "PROCESSING")
            {
                file.Status = "PROCESSING";
                _db.ImportFiles.Add(file);
                _db.SaveChanges();
                return new IngestContext(job, file, artifact, latest, true, published);
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

    private ImportedFileResult FinalizeParse(IngestContext ctx, ParsedImport parsed)
    {
        var attempt = _db.ParseAttempts.Single(a => a.Id == ctx.Attempt.Id);
        var file = _db.ImportFiles.Single(f => f.Id == ctx.File.Id);
        var job = _db.ImportJobs.Single(j => j.Id == ctx.Job.Id);

        PersistParseRows(attempt.Id, parsed);

        var blocking = parsed.HasFatalOrError();
        var published = false;
        string parseStatus;
        if (blocking)
        {
            parseStatus = parsed.Issues.Any(i => i.Severity == IssueSeverity.Fatal) ? "FAILED" : "INVALID";
        }
        else
        {
            var saleIds = parsed.Movements
                .Where(m => m.Direction == MovementDirection.Out && m.ExternalSaleId is not null)
                .Select(m => m.ExternalSaleId!)
                .Distinct()
                .ToList();
            var overlap = saleIds.Count > 0 && ExistsOverlappingPublishedSales(ctx.Artifact.Id, saleIds);
            if (overlap)
            {
                parseStatus = "WARNING";
                _db.ValidationResults.Add(new ValidationResultEntity(
                    Guid.NewGuid(),
                    attempt.Id,
                    "OVERLAPPING_REPORT",
                    "WARNING",
                    null,
                    null,
                    null,
                    null,
                    "identity-v1",
                    "canonical publication blocked"));
            }
            else
            {
                PublishCanonical(ctx.Artifact.Id, attempt.Id, parsed);
                published = true;
                parseStatus = parsed.Issues.Any(i => i.Severity == IssueSeverity.Warning) ? "WARNING" : "VALID";
            }
        }

        attempt.Status = parseStatus;
        attempt.RecordsFound = parsed.Movements.Count;
        attempt.CompletedAt = DateTimeOffset.UtcNow;
        attempt.LeaseUntil = null;
        file.Status = published ? "IMPORTED" : MapFileStatus(parseStatus);
        file.CompletedAt = DateTimeOffset.UtcNow;
        _db.SaveChanges();
        return ToResult(new IngestContext(job, file, ctx.Artifact, attempt, ctx.SkipParse, published), parsed, published);
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
        if (parsed.ExternalProductId is null)
        {
            return;
        }

        var product = _db.Products.SingleOrDefault(p => p.ExternalSource == Source && p.ExternalId == parsed.ExternalProductId)
                      ?? Add(new ProductEntity(
                          Guid.NewGuid(),
                          Source,
                          parsed.ExternalProductId,
                          parsed.ProductName ?? parsed.ExternalProductId,
                          attemptId));
        if (parsed.ProductName is not null && parsed.ProductName != product.Name)
        {
            product.SetName(parsed.ProductName);
        }

        var salesByExternal = _db.Sales
            .Where(s => s.ExternalSource == Source)
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
            using (var digest = IncrementalHash.CreateHash(HashAlgorithmName.SHA256))
            using (var output = File.Create(temp))
            {
                var buffer = new byte[8192];
                int read;
                while ((read = content.Read(buffer, 0, buffer.Length)) > 0)
                {
                    output.Write(buffer, 0, read);
                    digest.AppendData(buffer.AsSpan(0, read));
                }

                hash = digest.GetHashAndReset();
            }

            var bytes = File.ReadAllBytes(temp);
            var sha = Convert.ToHexString(hash).ToLowerInvariant();
            return new SpoolResult(bytes, sha);
        }
        finally
        {
            if (File.Exists(temp))
            {
                File.Delete(temp);
            }
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

    internal sealed record SpoolResult(byte[] Bytes, string Sha256);

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
