using System.Reflection;
using System.Security.Cryptography;
using Calciolari.DataHub.Imports.Application;
using Calciolari.DataHub.Imports.Domain.Hints;
using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;
using Calciolari.DataHub.Imports.Infrastructure.Storage;
using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Persistence.Entities;
using Calciolari.DataHub.Shared.Api;
using Calciolari.DataHub.Tests.Support;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Npgsql;

namespace Calciolari.DataHub.Tests;

public sealed class IngestionCoverageTests : IDisposable
{
    private readonly string _rawRoot = Directory.CreateTempSubdirectory("ingest-cov").FullName;
    private readonly DataHubDbContext _db;
    private readonly LocalRawFileStorage _storage;
    private readonly ScriptedParser _parser = new();
    private readonly ImportIngestionService _svc;

    public IngestionCoverageTests()
    {
        Environment.SetEnvironmentVariable("ASPNETCORE_ENVIRONMENT", "Development");
        _db = TestDb.Open();
        TestDb.Truncate(_db);
        _storage = new LocalRawFileStorage(_rawRoot);
        _svc = new ImportIngestionService(_storage, _parser, new FilenameHintsParser(), _db);
    }

    public void Dispose()
    {
        _db.Dispose();
        if (Directory.Exists(_rawRoot))
        {
            Directory.Delete(_rawRoot, true);
        }
    }

    [Fact]
    public void Ingest_publishes_renames_and_skips_incomplete_out_rows()
    {
        _parser.Default = _ => ValidImport(
            "41",
            "OLD",
            [
                Out("S1", 1m, 10m, 10m, 0),
                Out("S1", 2m, 10m, 20m, 1),
                new ParsedMovement(2, MovementDirection.In, "41", "OLD", "IN1", DateTime.Now, 1m, 1m, null, 1m, null, null, null, SourceLocator.Empty),
                new ParsedMovement(3, MovementDirection.Out, "41", "OLD", "S2", DateTime.Now, null, 1m, null, 1m, null, null, null, SourceLocator.Empty),
                new ParsedMovement(4, MovementDirection.Out, "41", "OLD", "S3", DateTime.Now, 1m, null, null, 1m, null, null, null, SourceLocator.Empty),
                new ParsedMovement(5, MovementDirection.Out, "41", "OLD", "S4", DateTime.Now, 1m, 1m, null, null, null, null, null, SourceLocator.Empty),
                new ParsedMovement(6, MovementDirection.Out, "41", "OLD", null, DateTime.Now, 1m, 1m, null, 1m, null, null, null, SourceLocator.Empty)
            ]);
        var first = _svc.Ingest(new MemoryStream([1, 2, 3]), null);
        Assert.True(first.Published);
        Assert.Equal("VALID", first.ParseStatus);

        _parser.Default = _ => ValidImport("88", null, [
            new ParsedMovement(0, MovementDirection.In, "88", null, "IN", DateTime.Now, 1m, 1m, null, 1m, null, null, null, SourceLocator.Empty)
        ]);
        var inOnly = _svc.Ingest(new MemoryStream([4, 5, 6]), "in-only.qrp");
        Assert.Equal("VALID", inOnly.ParseStatus);

        _parser.Default = _ => ValidImport("41", "NEW", [Out("S9", 1m, 1m, 1m)]);
        var renamed = _svc.Ingest(new MemoryStream([9, 9, 9]), "other.qrp");
        Assert.True(renamed.Published);
        Assert.Equal("NEW", _db.Products.AsNoTracking().Single(p => p.ExternalId == "41").Name);

        _parser.Default = _ => ValidImport("41", null, [Out("S10", 1m, 1m, 1m)]);
        var keepName = _svc.Ingest(new MemoryStream([8, 8, 8]), "keep.qrp");
        Assert.True(keepName.Published);
        Assert.Equal("NEW", _db.Products.AsNoTracking().Single(p => p.ExternalId == "41").Name);
    }

    [Fact]
    public void Empty_job_fails_and_unknown_job_rolls_back()
    {
        var jobId = _svc.CreateJob();
        _svc.CompleteJob(jobId);
        Assert.Equal("FAILED", _db.ImportJobs.AsNoTracking().Single(j => j.Id == jobId).Status);

        _parser.Default = _ => ValidImport("1", "n", [Out("x", 1m, 1m, 1m)]);
        Assert.Throws<ArgumentNullException>(() => _svc.IngestIntoJob(Guid.NewGuid(), null!, "x.qrp"));
        var ex = Assert.Throws<ArgumentException>(() =>
            _svc.IngestIntoJob(Guid.NewGuid(), new MemoryStream([1]), "x.qrp"));
        Assert.Contains("unknown job", ex.Message);
    }

    [Fact]
    public void Skip_parse_for_processing_valid_warning_invalid_and_retry_failed()
    {
        _parser.Default = _ => ValidImport("41", "N", [Out("S1", 1m, 1m, 1m)]);
        var published = _svc.Ingest(new MemoryStream([1]), "a.qrp");
        var dupPublished = _svc.Ingest(new MemoryStream([1]), "a2.qrp");
        Assert.True(dupPublished.Deduplicated);
        Assert.Equal("IMPORTED", dupPublished.FileStatus);

        _parser.Default = _ => ValidImport("41", "N", [Out("S1", 1m, 1m, 1m)]);
        var overlap = _svc.Ingest(new MemoryStream([2]), "b.qrp");
        Assert.Equal("WARNING", overlap.ParseStatus);
        Assert.False(overlap.Published);
        var dupWarning = _svc.Ingest(new MemoryStream([2]), "b2.qrp");
        Assert.True(dupWarning.Deduplicated);
        Assert.Equal("WARNING", dupWarning.FileStatus);

        _parser.Default = _ => IssueImport(IssueSeverity.Error);
        var invalid = _svc.Ingest(new MemoryStream([3]), "c.qrp");
        Assert.Equal("INVALID", invalid.ParseStatus);
        var dupInvalid = _svc.Ingest(new MemoryStream([3]), "c2.qrp");
        Assert.Equal("INVALID", dupInvalid.FileStatus);

        _parser.Default = _ => IssueImport(IssueSeverity.Fatal);
        var failed = _svc.Ingest(new MemoryStream([4]), "d.qrp");
        Assert.Equal("FAILED", failed.ParseStatus);
        _parser.Default = _ => IssueImport(IssueSeverity.Fatal);
        var retried = _svc.Ingest(new MemoryStream([4]), "d2.qrp");
        Assert.True(retried.Deduplicated);
        Assert.Equal("FAILED", retried.ParseStatus);
        MarkAllFilesDeduplicated(failed.RawArtifactId);
        var retriedNoOriginal = _svc.Ingest(new MemoryStream([4]), "d3.qrp");
        Assert.True(retriedNoOriginal.Deduplicated);
        Assert.Null(_db.ImportFiles.AsNoTracking().Single(f => f.Id == retriedNoOriginal.ImportFileId).DuplicateOfImportFileId);

        _parser.Default = _ => ValidImport(null, null, [Out("Z", 1m, 1m, 1m)]);
        var noProduct = _svc.Ingest(new MemoryStream([5]), "e.qrp");
        Assert.Equal("VALID", noProduct.ParseStatus);
        var dupNoProduct = _svc.Ingest(new MemoryStream([5]), "e2.qrp");
        Assert.Equal("IMPORTED", dupNoProduct.FileStatus);

        var processingBytes = new byte[] { 6 };
        _parser.Default = _ => ValidImport("7", "P", [Out("P1", 1m, 1m, 1m)]);
        var processing = _svc.Ingest(new MemoryStream(processingBytes), "p.qrp");
        var attempt = _db.ParseAttempts.Single(a => a.Id == processing.ParseAttemptId);
        attempt.Status = "PROCESSING";
        attempt.LeaseUntil = DateTimeOffset.UtcNow.AddMinutes(5);
        _db.SaveChanges();
        var skippedProcessing = _svc.Ingest(new MemoryStream(processingBytes), "p2.qrp");
        Assert.Equal("PROCESSING", skippedProcessing.FileStatus);

        attempt.Status = "PENDING";
        _db.SaveChanges();
        var skippedPending = _svc.Ingest(new MemoryStream(processingBytes), "p2-pending.qrp");
        Assert.Equal("PROCESSING", skippedPending.FileStatus);

        MarkAllFilesDeduplicated(processing.RawArtifactId);
        var skippedNoOriginal = _svc.Ingest(new MemoryStream(processingBytes), "p3.qrp");
        Assert.Equal("PROCESSING", skippedNoOriginal.FileStatus);
        Assert.Null(_db.ImportFiles.AsNoTracking().Single(f => f.Id == skippedNoOriginal.ImportFileId).DuplicateOfImportFileId);
    }

    [Fact]
    public void Warning_issues_publish_and_reprocess_overlap()
    {
        _parser.Default = _ => new ParsedImport(
            "INTERPDV", InterPdvQrpParser.ParserName, InterPdvQrpParser.ParserVersion,
            "41", "NAME",
            [Out("W1", 1m, 10m, 10m)],
            new ParsedImportTotals(null, 1m, null, 10m, null, null),
            ParsedImportStats.Empty,
            [ParseIssue.Create("W", IssueSeverity.Warning, IssueStage.Validation, null, "warn")]);
        var warned = _svc.Ingest(new MemoryStream([7]), "w.qrp");
        Assert.Equal("WARNING", warned.ParseStatus);
        Assert.True(warned.Published);

        _parser.Default = _ => ValidImport("41", "NAME", [Out("W1", 1m, 10m, 10m)]);
        var other = _svc.Ingest(new MemoryStream([8]), "w2.qrp");
        Assert.Equal("WARNING", other.ParseStatus);

        var reprocessed = _svc.Reprocess(other.ImportFileId);
        Assert.Equal("WARNING", reprocessed.ParseStatus);
        Assert.False(reprocessed.Published);
    }

    [Fact]
    public void Reprocess_integrity_io_runtime_lease_and_fail_attempt()
    {
        _parser.Default = _ => ValidImport("41", "N", [Out("R1", 1m, 1m, 1m)]);
        var ingested = _svc.Ingest(new MemoryStream([10]), "r.qrp");

        var artifact = _db.RawArtifacts.Single(a => a.Id == ingested.RawArtifactId);
        var path = Path.Combine(_rawRoot, artifact.StorageKey);
        File.WriteAllBytes(path, [0x00, 0x01]);
        var integrity = Assert.Throws<ApiException>(() => _svc.Reprocess(ingested.ImportFileId));
        Assert.Equal(StatusCodes.Status409Conflict, integrity.StatusCode);
        File.WriteAllBytes(path, [10]);

        var wrapping = new DelegatingStorage { Inner = _storage };
        var svc2 = new ImportIngestionService(wrapping, _parser, new FilenameHintsParser(), _db);
        wrapping.OpenOverride = (key, sha, size) =>
        {
            if (wrapping.OpenCount >= 2)
            {
                throw new RawStorageIntegrityException("late hash");
            }

            return _storage.OpenVerified(key, sha, size);
        };
        var late = Assert.Throws<ApiException>(() => svc2.Reprocess(ingested.ImportFileId));
        Assert.Equal(StatusCodes.Status409Conflict, late.StatusCode);

        wrapping.OpenCount = 0;
        wrapping.OpenOverride = (key, sha, size) =>
        {
            if (wrapping.OpenCount >= 2)
            {
                throw new IOException("read failed");
            }

            return _storage.OpenVerified(key, sha, size);
        };
        Assert.Throws<IOException>(() => svc2.Reprocess(ingested.ImportFileId));

        wrapping.OpenOverride = null;
        wrapping.OpenCount = 0;
        _parser.Default = _ => throw new InvalidOperationException("parse blew up");
        Assert.Throws<InvalidOperationException>(() => _svc.Reprocess(ingested.ImportFileId));

        _parser.Default = _ => throw new ApiException(StatusCodes.Status400BadRequest, "parser api");
        Assert.Throws<ApiException>(() => _svc.Reprocess(ingested.ImportFileId));
        foreach (var attempt in _db.ParseAttempts)
        {
            attempt.Status = "FAILED";
            attempt.LeaseUntil = null;
        }

        _db.SaveChanges();

        _parser.Default = _ =>
        {
            _db.SaleItems.RemoveRange(_db.SaleItems);
            _db.Sales.RemoveRange(_db.Sales);
            _db.Products.RemoveRange(_db.Products);
            _db.ValidationResults.RemoveRange(_db.ValidationResults);
            _db.ParsedMovements.RemoveRange(_db.ParsedMovements);
            _db.ArtifactPublications.RemoveRange(_db.ArtifactPublications);
            foreach (var file in _db.ImportFiles.ToList())
            {
                file.ParseAttemptId = null;
            }

            _db.SaveChanges();
            _db.ParseAttempts.RemoveRange(_db.ParseAttempts);
            _db.SaveChanges();
            throw new InvalidOperationException("attempt deleted");
        };
        Assert.Throws<InvalidOperationException>(() => _svc.Reprocess(ingested.ImportFileId));
    }

    [Fact]
    public void Reprocess_lease_lost_in_progress_expired_and_missing_file()
    {
        _parser.Default = _ => ValidImport("41", "N", [Out("L1", 1m, 1m, 1m)]);
        var ingested = _svc.Ingest(new MemoryStream([11]), "lease.qrp");

        _parser.Default = _ =>
        {
            foreach (var attempt in _db.ParseAttempts.Local.Where(a => a.Status == "PROCESSING"))
            {
                attempt.LeaseOwner = "stolen";
            }

            _db.SaveChanges();
            return ValidImport("41", "N", [Out("L1", 1m, 1m, 1m)]);
        };
        var lost = Assert.Throws<ApiException>(() => _svc.Reprocess(ingested.ImportFileId));
        Assert.Equal(StatusCodes.Status409Conflict, lost.StatusCode);

        var latest = _db.ParseAttempts.OrderByDescending(a => a.AttemptCount).First(a => a.RawArtifactId == ingested.RawArtifactId);
        latest.Status = "PENDING";
        latest.LeaseUntil = DateTimeOffset.UtcNow.AddMinutes(5);
        latest.LeaseOwner = "other";
        _db.SaveChanges();
        var pendingBusy = Assert.Throws<ApiException>(() => _svc.Reprocess(ingested.ImportFileId));
        Assert.Contains("in progress", pendingBusy.Message);

        latest.Status = "PROCESSING";
        latest.LeaseUntil = DateTimeOffset.UtcNow.AddMinutes(5);
        latest.LeaseOwner = "other";
        _db.SaveChanges();
        var busy = Assert.Throws<ApiException>(() => _svc.Reprocess(ingested.ImportFileId));
        Assert.Contains("in progress", busy.Message);

        _db.Database.ExecuteSqlRaw(
            """UPDATE parse_attempt SET lease_until = NOW() - INTERVAL '10 minutes' WHERE raw_artifact_id = {0}""",
            ingested.RawArtifactId);
        _db.ChangeTracker.Clear();
        _parser.Default = _ => ValidImport("41", "N", [Out("L1", 1m, 1m, 1m)]);
        var afterExpiry = _svc.Reprocess(ingested.ImportFileId);
        Assert.True(afterExpiry.Published);

        _db.Database.ExecuteSqlRaw(
            """UPDATE parse_attempt SET status = 'FAILED', lease_until = NULL WHERE raw_artifact_id = {0}""",
            ingested.RawArtifactId);
        _db.ChangeTracker.Clear();
        var noLease = _svc.Reprocess(ingested.ImportFileId);
        Assert.True(noLease.Published);

        Assert.Throws<ApiException>(() => _svc.Reprocess(Guid.NewGuid()));

        _parser.Default = _ => ValidImport("41", "N", [Out("EXPIRED1", 1m, 1m, 1m)]);
        var expiredIngest = _svc.Ingest(new MemoryStream([33]), "expired.qrp");
        _db.ChangeTracker.Clear();
        var expiredAttempt = _db.ParseAttempts.Single(a => a.Id == expiredIngest.ParseAttemptId);
        expiredAttempt.Status = "PROCESSING";
        expiredAttempt.LeaseUntil = DateTimeOffset.UtcNow.AddHours(-1);
        expiredAttempt.LeaseOwner = "stale";
        _db.SaveChanges();
        _db.ChangeTracker.Clear();
        _parser.Default = _ => ValidImport("41", "N", [Out("EXPIRED1", 1m, 1m, 1m)]);
        var afterStale = _svc.Reprocess(expiredIngest.ImportFileId);
        Assert.True(afterStale.Published);
    }

    [Fact]
    public void Reprocess_fatal_and_seeded_artifact_without_attempts()
    {
        _parser.Default = _ => ValidImport("41", "N", [Out("F1", 1m, 1m, 1m)]);
        var ingested = _svc.Ingest(new MemoryStream([12]), "fatal.qrp");
        _parser.Default = _ => IssueImport(IssueSeverity.Fatal);
        var failed = _svc.Reprocess(ingested.ImportFileId);
        Assert.Equal("FAILED", failed.ParseStatus);
        Assert.False(failed.Published);

        _parser.Default = _ => IssueImport(IssueSeverity.Error);
        var invalid = _svc.Reprocess(ingested.ImportFileId);
        Assert.Equal("INVALID", invalid.ParseStatus);

        _parser.Default = _ => new ParsedImport(
            "INTERPDV", InterPdvQrpParser.ParserName, InterPdvQrpParser.ParserVersion,
            "41", "N",
            [Out("F1", 1m, 1m, 1m)],
            new ParsedImportTotals(null, 1m, null, 1m, null, null),
            ParsedImportStats.Empty,
            [ParseIssue.Create("W", IssueSeverity.Warning, IssueStage.Validation, null, "warn")]);
        var warned = _svc.Reprocess(ingested.ImportFileId);
        Assert.Equal("WARNING", warned.ParseStatus);
        Assert.True(warned.Published);

        _parser.Default = _ => ValidImport("41", "N", [
            new ParsedMovement(0, MovementDirection.In, "41", "N", "IN", DateTime.Now, 1m, 1m, null, 1m, null, null, null, SourceLocator.Empty)
        ]);
        var noSales = _svc.Reprocess(ingested.ImportFileId);
        Assert.Equal("VALID", noSales.ParseStatus);

        _parser.Default = _ => ValidImport("41", "N", [Out("F1", 1m, 1m, 1m)]);
        var sameName = _svc.Ingest(new MemoryStream([14]), "same-name.qrp");
        Assert.True(sameName.Published);

        var bytes = new byte[] { 13 };
        var sha = Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
        var stored = _storage.PutIfAbsent(new MemoryStream(bytes), RawFileDescriptor.Create(sha, bytes.Length, "QRP"));
        var job = new ImportJobEntity(Guid.NewGuid(), "PROCESSING");
        var artifact = new RawArtifactEntity(Guid.NewGuid(), sha, bytes.Length, stored.StorageKey, "QRP");
        var file = new ImportFileEntity(Guid.NewGuid(), job.Id, artifact.Id, "seed.qrp", "INTERPDV", "PENDING");
        _db.ImportJobs.Add(job);
        _db.RawArtifacts.Add(artifact);
        _db.SaveChanges();
        _db.ImportFiles.Add(file);
        _db.SaveChanges();
        _parser.Default = _ => ValidImport("99", "SEED", [Out("SEED1", 1m, 1m, 1m)]);
        var seeded = _svc.Reprocess(file.Id);
        Assert.True(seeded.Published);
        Assert.Equal(1, seeded.RecordsFound);
    }

    [Fact]
    public void FailAttempt_null_attempt_and_summary_truncation()
    {
        var job = new ImportJobEntity(Guid.NewGuid(), "PROCESSING");
        var artifact = new RawArtifactEntity(Guid.NewGuid(), new string('a', 64), 1, "aa/aa/" + new string('a', 64), "QRP");
        _db.ImportJobs.Add(job);
        _db.RawArtifacts.Add(artifact);
        _db.SaveChanges();
        var attempt = new ParseAttemptEntity(Guid.NewGuid(), artifact.Id, InterPdvQrpParser.ParserName, InterPdvQrpParser.ParserVersion, "PROCESSING", 1);
        _db.ParseAttempts.Add(attempt);
        _db.SaveChanges();

        _svc.FailAttempt(Guid.NewGuid(), "missing");
        _svc.FailAttempt(attempt.Id, null);
        Assert.Equal("FAILED", _db.ParseAttempts.Single(a => a.Id == attempt.Id).Status);
        Assert.Null(_db.ParseAttempts.Single(a => a.Id == attempt.Id).ErrorSummary);

        attempt.Status = "PROCESSING";
        _db.SaveChanges();
        _svc.FailAttempt(attempt.Id, new string('x', 600));
        Assert.Equal(500, _db.ParseAttempts.Single(a => a.Id == attempt.Id).ErrorSummary!.Length);
    }

    [Fact]
    public void Unique_violation_retries_then_gives_up()
    {
        var interceptor = new UniqueViolationInterceptor();
        using var db = TestDb.Open(interceptor);
        TestDb.Truncate(db);
        var storage = new LocalRawFileStorage(Directory.CreateTempSubdirectory("uniq").FullName);
        var parser = new ScriptedParser { Default = _ => ValidImport("1", "n", [Out("s", 1m, 1m, 1m)]) };
        var svc = new ImportIngestionService(storage, parser, new FilenameHintsParser(), db);

        var jobId = svc.CreateJob();
        interceptor.ThrowTimes = 1;
        var recovered = svc.IngestIntoJob(jobId, new MemoryStream([1]), "u.qrp");
        Assert.Equal("VALID", recovered.ParseStatus);

        interceptor.ThrowTimes = 0;
        var job2 = svc.CreateJob();
        interceptor.ThrowTimes = 10;
        var exhausted = Assert.Throws<InvalidOperationException>(() =>
            svc.IngestIntoJob(job2, new MemoryStream([2]), "u2.qrp"));
        Assert.Contains("failed to open/create artifact", exhausted.Message);

        Assert.False(ImportIngestionService.IsUniqueViolation(new DbUpdateException("x", new InvalidOperationException("nope"))));
        var ctor = typeof(PostgresException).GetConstructors(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic)
            .First(c =>
            {
                var args = c.GetParameters();
                return args.Length == 4 && args.All(p => p.ParameterType == typeof(string));
            });
        var pg = (PostgresException)ctor.Invoke(["dup", "ERROR", "ERROR", PostgresErrorCodes.UniqueViolation]);
        Assert.True(ImportIngestionService.IsUniqueViolation(new DbUpdateException("dup", pg)));
    }

    [Fact]
    public void Query_file_wrong_job_and_null_attempt()
    {
        _parser.Default = _ => ValidImport("41", "N", [Out("Q1", 1m, 1m, 1m)]);
        var ingested = _svc.Ingest(new MemoryStream([21]), "q.qrp");
        var queries = new ImportQueryService(_db);
        Assert.Throws<ApiException>(() => queries.GetFile(ingested.JobId, Guid.NewGuid()));
        Assert.Throws<ApiException>(() => queries.GetFile(Guid.NewGuid(), ingested.ImportFileId));

        var file = _db.ImportFiles.Single(f => f.Id == ingested.ImportFileId);
        file.ParseAttemptId = null;
        _db.SaveChanges();
        var detail = queries.GetFile(ingested.JobId, ingested.ImportFileId);
        Assert.Null(detail.ParseStatus);
        Assert.Empty(detail.Validations);
    }

    [Fact]
    public void Dashboard_and_sales_filters()
    {
        _parser.Default = _ => ValidImport("41", "N", [Out("Q1", 1m, 1m, 1m)]);
        _svc.Ingest(new MemoryStream([22]), "dash.qrp");
        var productId = _db.Products.AsNoTracking().Single().Id;
        var dash = new Calciolari.DataHub.Analytics.Application.DashboardQueryService(_db);
        var summary = dash.Summarize(productId, new DateTime(2020, 1, 1), new DateTime(2099, 12, 31, 23, 59, 59));
        Assert.True(summary.SalesCount >= 1);
        var sales = new Calciolari.DataHub.Sales.Application.SaleQueryService(_db);
        var page = sales.List(productId, new DateTime(2020, 1, 1), new DateTime(2099, 12, 31, 23, 59, 59), 0, 20);
        Assert.True(page.TotalElements >= 1);
    }

    private void MarkAllFilesDeduplicated(Guid artifactId)
    {
        foreach (var file in _db.ImportFiles.Where(f => f.RawArtifactId == artifactId))
        {
            file.Deduplicated = true;
        }

        _db.SaveChanges();
    }

    private static ParsedImport ValidImport(string? productId, string? name, IReadOnlyList<ParsedMovement> movements) =>
        new(
            "INTERPDV",
            InterPdvQrpParser.ParserName,
            InterPdvQrpParser.ParserVersion,
            productId,
            name,
            movements,
            new ParsedImportTotals(null, 1m, null, 1m, null, null),
            ParsedImportStats.Empty,
            []);

    private static ParsedImport IssueImport(IssueSeverity severity) =>
        new(
            "INTERPDV",
            InterPdvQrpParser.ParserName,
            InterPdvQrpParser.ParserVersion,
            null,
            null,
            [],
            ParsedImportTotals.Empty,
            ParsedImportStats.Empty,
            [ParseIssue.Create("X", severity, IssueStage.Container, null, "x")]);

    private static ParsedMovement Out(string saleId, decimal qty, decimal price, decimal total, int index = 0) =>
        new(index, MovementDirection.Out, "41", "NAME", saleId, new DateTime(2026, 8, 7, 12, 0, 0), qty, price, 0m, total, null, null, null, SourceLocator.Empty);

    private sealed class ScriptedParser : IImportParser
    {
        public Func<ParserInput, ParsedImport>? Default { get; set; }

        public bool Supports(ParserInput input) => true;

        public ParsedImport Parse(ParserInput input) => Default!(input);
    }

    private sealed class DelegatingStorage : IRawFileStorage
    {
        public required IRawFileStorage Inner { get; init; }
        public Func<string, string, long, Stream>? OpenOverride { get; set; }
        public int OpenCount { get; set; }

        public StoredRawFile PutIfAbsent(Stream bytes, RawFileDescriptor descriptor) => Inner.PutIfAbsent(bytes, descriptor);

        public Stream OpenVerified(string storageKey, string expectedSha256, long expectedSize)
        {
            OpenCount++;
            return OpenOverride is not null
                ? OpenOverride(storageKey, expectedSha256, expectedSize)
                : Inner.OpenVerified(storageKey, expectedSha256, expectedSize);
        }

        public bool Exists(string storageKey) => Inner.Exists(storageKey);
    }

    private sealed class UniqueViolationInterceptor : SaveChangesInterceptor
    {
        public int ThrowTimes { get; set; }

        public override InterceptionResult<int> SavingChanges(DbContextEventData eventData, InterceptionResult<int> result)
        {
            if (ThrowTimes > 0)
            {
                ThrowTimes--;
                var ctor = typeof(PostgresException).GetConstructors(BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic)
                    .First(c =>
                    {
                        var args = c.GetParameters();
                        return args.Length == 4 && args.All(p => p.ParameterType == typeof(string));
                    });
                var pg = (PostgresException)ctor.Invoke(["dup", "ERROR", "ERROR", PostgresErrorCodes.UniqueViolation]);
                throw new DbUpdateException("unique", pg);
            }

            return result;
        }
    }
}
