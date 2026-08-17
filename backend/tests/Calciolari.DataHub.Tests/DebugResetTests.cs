using Calciolari.DataHub.Debug;
using Calciolari.DataHub.Debug.Api;
using Calciolari.DataHub.Imports.Application;
using Calciolari.DataHub.Imports.Infrastructure.Storage;
using Calciolari.DataHub.Persistence.Entities;
using Calciolari.DataHub.Shared.Api;
using Calciolari.DataHub.Tests.Support;
using Microsoft.AspNetCore.Http;
using System.Security.Cryptography;
using System.Text.Json;

namespace Calciolari.DataHub.Tests;

public sealed class DebugControllerTests
{
    [Fact]
    public void Status_and_reset_gate_on_debug_flag()
    {
        var disabled = new DebugController(new DataHubOptions { DebugEnabled = false }, null!);
        Assert.False(disabled.Status().Enabled);
        var hidden = Assert.Throws<ApiException>(() => disabled.Reset());
        Assert.Equal(StatusCodes.Status404NotFound, hidden.StatusCode);

        DatasetResetResponse? captured = null;
        var enabled = new DebugController(
            new DataHubOptions { DebugEnabled = true },
            new StubReset(() => captured = new DatasetResetResponse(true, 3, 4)));
        Assert.True(enabled.Status().Enabled);
        var result = enabled.Reset();
        Assert.True(result.Reset);
        Assert.Equal(3, result.ArtifactCount);
        Assert.Equal(4, result.FilesDeleted);
        Assert.NotNull(captured);
    }

    private sealed class StubReset : IDatasetResetService
    {
        private readonly Func<DatasetResetResponse> _reset;

        public StubReset(Func<DatasetResetResponse> reset) => _reset = reset;

        public DatasetResetResponse Reset() => _reset();
    }
}

public sealed class DatasetResetServiceTests
{
    [Fact]
    public void Reset_truncates_canonical_tables_wipes_raw_files_and_clears_metrics()
    {
        using var db = TestDb.Open();
        TestDb.Truncate(db);
        var root = Directory.CreateTempSubdirectory("debug-reset").FullName;
        var storage = new LocalRawFileStorage(root);
        var bytes = "wipe-me"u8.ToArray();
        var sha = Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
        var stored = storage.PutIfAbsent(new MemoryStream(bytes), RawFileDescriptor.Create(sha, bytes.Length, "QRP"));
        Assert.True(storage.Exists(stored.StorageKey));

        db.RawArtifacts.Add(new RawArtifactEntity(Guid.NewGuid(), sha, bytes.Length, stored.StorageKey, "QRP"));
        db.SaveChanges();

        var metrics = new ImportMetrics();
        metrics.RecordJobCompleted("SUCCEEDED");
        metrics.AddRawBytes(bytes.Length);

        var service = new DatasetResetService(db, storage, metrics);
        var result = service.Reset();
        Assert.True(result.Reset);
        Assert.Equal(1, result.ArtifactCount);
        Assert.True(result.FilesDeleted >= 1);
        Assert.Equal(0, db.RawArtifacts.Count());
        Assert.False(storage.Exists(stored.StorageKey));

        var json = JsonSerializer.Serialize(metrics.Snapshot());
        Assert.Contains("imports.completed", json);
        metrics.RecordJobCompleted("SUCCEEDED");
        var afterOne = JsonSerializer.Serialize(metrics.Snapshot());
        Assert.Contains("\"value\":1", afterOne);
        Assert.DoesNotContain("\"value\":2", afterOne);

        var empty = service.Reset();
        Assert.Equal(0, empty.ArtifactCount);
        Assert.Equal(0, empty.FilesDeleted);
    }
}
