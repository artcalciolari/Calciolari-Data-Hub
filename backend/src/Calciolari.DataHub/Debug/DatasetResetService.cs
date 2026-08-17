using Calciolari.DataHub.Imports.Application;
using Calciolari.DataHub.Imports.Infrastructure.Storage;
using Calciolari.DataHub.Persistence;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Debug;

public interface IDatasetResetService
{
    DatasetResetResponse Reset();
}

public sealed class DatasetResetService : IDatasetResetService
{
    internal const string TruncateSql = """
        TRUNCATE TABLE
          sale_item, sale, product, validation_result, parsed_movement,
          artifact_publication, import_file, parse_attempt, import_job, raw_artifact
        RESTART IDENTITY CASCADE
        """;

    private readonly DataHubDbContext _db;
    private readonly IRawFileStorage _storage;
    private readonly ImportMetrics _metrics;

    public DatasetResetService(DataHubDbContext db, IRawFileStorage storage, ImportMetrics metrics)
    {
        _db = db;
        _storage = storage;
        _metrics = metrics;
    }

    public DatasetResetResponse Reset()
    {
        var artifactCount = _db.RawArtifacts.Count();
        _db.Database.ExecuteSqlRaw(TruncateSql);
        _db.ChangeTracker.Clear();
        var filesDeleted = _storage.WipeAll();
        _metrics.Reset();
        return new DatasetResetResponse(true, artifactCount, filesDeleted);
    }
}

public sealed record DatasetResetResponse(bool Reset, int ArtifactCount, int FilesDeleted);

public sealed record DebugStatusResponse(bool Enabled);
