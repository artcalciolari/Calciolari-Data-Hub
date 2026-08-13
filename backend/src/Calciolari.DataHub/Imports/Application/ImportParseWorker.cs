using Calciolari.DataHub.Persistence;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

namespace Calciolari.DataHub.Imports.Application;

public interface IImportFileProcessor
{
    ImportedFileResult ProcessAcceptedFile(Guid importFileId);

    void ReclaimExpiredProcessingLeases();
}

public sealed class ImportWorkerOptions
{
    public TimeSpan ReclaimInterval { get; set; } = TimeSpan.FromSeconds(2);

    public TimeSpan OrphanMinAge { get; set; } = TimeSpan.FromHours(1);
}

public static class ImportParseWorkerLoop
{
    public static async Task Run(
        IImportWorkQueue queue,
        Func<Guid, CancellationToken, Task> process,
        Func<CancellationToken, Task> reclaim,
        TimeSpan reclaimInterval,
        CancellationToken cancellationToken,
        Action<Exception>? onError = null)
    {
        ArgumentNullException.ThrowIfNull(queue);
        ArgumentNullException.ThrowIfNull(process);
        ArgumentNullException.ThrowIfNull(reclaim);
        if (reclaimInterval <= TimeSpan.Zero)
        {
            reclaimInterval = TimeSpan.FromMilliseconds(1);
        }

        await Task.WhenAll(
            Consume(queue, process, cancellationToken, onError),
            Reclaim(reclaim, reclaimInterval, cancellationToken, onError));
    }

    private static async Task Consume(
        IImportWorkQueue queue,
        Func<Guid, CancellationToken, Task> process,
        CancellationToken cancellationToken,
        Action<Exception>? onError)
    {
        try
        {
            await foreach (var id in queue.ReadAllAsync(cancellationToken))
            {
                try
                {
                    await process(id, cancellationToken);
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    onError?.Invoke(ex);
                }
            }
        }
        catch (OperationCanceledException)
        {
            if (!cancellationToken.IsCancellationRequested)
            {
                throw;
            }
        }
    }

    private static async Task Reclaim(
        Func<CancellationToken, Task> reclaim,
        TimeSpan interval,
        CancellationToken cancellationToken,
        Action<Exception>? onError)
    {
        try
        {
            while (true)
            {
                await Task.Delay(interval, cancellationToken);
                try
                {
                    await reclaim(cancellationToken);
                }
                catch (Exception ex) when (ex is not OperationCanceledException)
                {
                    onError?.Invoke(ex);
                }
            }
        }
        catch (OperationCanceledException)
        {
            if (!cancellationToken.IsCancellationRequested)
            {
                throw;
            }
        }
    }
}

public sealed class ImportParseWorker : BackgroundService
{
    private readonly IImportWorkQueue _queue;
    private readonly IServiceScopeFactory _scopes;
    private readonly ImportWorkerOptions _options;
    private readonly ILogger<ImportParseWorker> _logger;

    public ImportParseWorker(
        IImportWorkQueue queue,
        IServiceScopeFactory scopes,
        IOptions<ImportWorkerOptions> options,
        ILogger<ImportParseWorker> logger)
    {
        _queue = queue;
        _scopes = scopes;
        _options = options.Value;
        _logger = logger;
    }

    protected override Task ExecuteAsync(CancellationToken stoppingToken) =>
        ImportParseWorkerLoop.Run(
            _queue,
            ProcessOne,
            ReclaimOne,
            _options.ReclaimInterval,
            stoppingToken,
            ex => _logger.LogError(ex, "Import parse worker failed"));

    private Task ProcessOne(Guid importFileId, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        using var scope = _scopes.CreateScope();
        scope.ServiceProvider.GetRequiredService<IImportFileProcessor>().ProcessAcceptedFile(importFileId);
        return Task.CompletedTask;
    }

    private Task ReclaimOne(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        using var scope = _scopes.CreateScope();
        var processor = scope.ServiceProvider.GetRequiredService<IImportFileProcessor>();
        processor.ReclaimExpiredProcessingLeases();
        var reconciler = scope.ServiceProvider.GetService<IRawStorageReconciler>();
        if (reconciler is not null)
        {
            var orphans = reconciler.ReportOrphans(_options.OrphanMinAge);
            if (orphans.Count > 0)
            {
                _logger.LogWarning("Raw storage orphan report count={Count}", orphans.Count);
            }
        }

        return Task.CompletedTask;
    }
}

public sealed record OrphanRawFile(string RelativePath, long ByteSize, DateTime LastWriteUtc);

public interface IRawStorageReconciler
{
    IReadOnlyList<OrphanRawFile> ReportOrphans(TimeSpan minAge);
}

public sealed class RawStorageReconciler : IRawStorageReconciler
{
    private readonly DataHubDbContext _db;
    private readonly string _root;

    public RawStorageReconciler(DataHubDbContext db, DataHubOptions options)
    {
        _db = db;
        _root = Path.GetFullPath(options.RawStorageRoot);
    }

    public IReadOnlyList<OrphanRawFile> ReportOrphans(TimeSpan minAge)
    {
        if (!Directory.Exists(_root))
        {
            return [];
        }

        var known = _db.RawArtifacts.AsNoTracking().Select(a => a.StorageKey).ToHashSet(StringComparer.Ordinal);
        var cutoff = DateTime.UtcNow - minAge;
        var orphans = new List<OrphanRawFile>();
        foreach (var path in Directory.EnumerateFiles(_root, "*", SearchOption.AllDirectories))
        {
            var name = Path.GetFileName(path);
            if (name.StartsWith('.'))
            {
                continue;
            }

            var relative = Path.GetRelativePath(_root, path).Replace('\\', '/');
            if (known.Contains(relative))
            {
                continue;
            }

            var info = new FileInfo(path);
            if (info.LastWriteTimeUtc > cutoff)
            {
                continue;
            }

            orphans.Add(new OrphanRawFile(relative, info.Length, info.LastWriteTimeUtc));
        }

        return orphans;
    }
}
