using Calciolari.DataHub.Imports.Application;
using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Tests.Support;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;

namespace Calciolari.DataHub.Tests;

public sealed class ImportWorkerTests
{
    [Fact]
    public void Work_queue_rejects_writes_after_complete()
    {
        var queue = new ImportWorkQueue();
        queue.Enqueue(Guid.NewGuid());
        Assert.True(queue.TryDequeue(out _));
        queue.Complete();
        Assert.Throws<InvalidOperationException>(() => queue.Enqueue(Guid.NewGuid()));
    }

    [Fact]
    public void Metrics_record_each_counter_branch()
    {
        var metrics = new ImportMetrics();
        metrics.RecordJobCompleted("PROCESSING");
        metrics.RecordJobCompleted("SUCCEEDED");
        metrics.RecordJobCompleted("PARTIAL_SUCCESS");
        metrics.RecordJobCompleted("FAILED");
        metrics.RecordFile("IMPORTED", false, 0);
        metrics.RecordFile("WARNING", true, 5);
        metrics.RecordFile("INVALID", false, 1);
        metrics.RecordFile("FAILED", true, 2);
        metrics.AddRawBytes(0);
        metrics.AddRawBytes(10);
        metrics.Reset();
        metrics.RecordJobCompleted("SUCCEEDED");
        var json = System.Text.Json.JsonSerializer.Serialize(metrics.Snapshot());
        Assert.Contains("imports.completed", json);
        Assert.Contains("imports.duplicates", json);
        Assert.Contains("raw.storage.bytes", json);
        Assert.Contains("\"value\":1", json);
        Assert.DoesNotContain("\"value\":10", json);
    }

    [Fact]
    public async Task Loop_processes_errors_and_reclaims()
    {
        var queue = new ImportWorkQueue();
        var processed = new List<Guid>();
        var errors = new List<string>();
        var reclaims = 0;
        var first = Guid.NewGuid();
        var second = Guid.NewGuid();
        using var cts = new CancellationTokenSource();
        var run = ImportParseWorkerLoop.Run(
            queue,
            (id, _) =>
            {
                if (id == first)
                {
                    throw new InvalidOperationException("boom");
                }

                processed.Add(id);
                return Task.CompletedTask;
            },
            _ =>
            {
                reclaims++;
                if (reclaims == 1)
                {
                    throw new InvalidOperationException("reclaim-fail");
                }

                return Task.CompletedTask;
            },
            TimeSpan.Zero,
            cts.Token,
            ex => errors.Add(ex.Message));
        queue.Enqueue(first);
        queue.Enqueue(second);
        var deadline = DateTime.UtcNow.AddSeconds(3);
        while ((processed.Count == 0 || reclaims < 2 || errors.Count < 2) && DateTime.UtcNow < deadline)
        {
            await Task.Delay(20);
        }

        await cts.CancelAsync();
        await run;
        Assert.Contains(second, processed);
        Assert.Contains("boom", errors);
        Assert.Contains("reclaim-fail", errors);
        Assert.True(reclaims >= 2);
    }

    [Fact]
    public async Task Loop_swallows_cancellation_during_in_flight_work()
    {
        var queue = new ImportWorkQueue();
        using var cts = new CancellationTokenSource();
        var processStarted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var reclaimStarted = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        var run = ImportParseWorkerLoop.Run(
            queue,
            async (_, ct) =>
            {
                processStarted.TrySetResult();
                await Task.Delay(TimeSpan.FromSeconds(10), ct);
            },
            async ct =>
            {
                reclaimStarted.TrySetResult();
                await Task.Delay(TimeSpan.FromSeconds(10), ct);
            },
            TimeSpan.FromMilliseconds(1),
            cts.Token);
        queue.Enqueue(Guid.NewGuid());
        await Task.WhenAll(processStarted.Task.WaitAsync(TimeSpan.FromSeconds(3)),
            reclaimStarted.Task.WaitAsync(TimeSpan.FromSeconds(3)));
        await cts.CancelAsync();
        await run;
    }

    [Fact]
    public async Task Loop_continues_when_onError_is_null()
    {
        var queue = new ImportWorkQueue();
        var processed = new List<Guid>();
        var boom = Guid.NewGuid();
        var ok = Guid.NewGuid();
        using var cts = new CancellationTokenSource();
        var run = ImportParseWorkerLoop.Run(
            queue,
            (id, _) =>
            {
                if (id == boom)
                {
                    throw new InvalidOperationException("ignored");
                }

                processed.Add(id);
                return Task.CompletedTask;
            },
            _ => throw new InvalidOperationException("reclaim-ignored"),
            TimeSpan.FromMilliseconds(1),
            cts.Token);
        queue.Enqueue(boom);
        queue.Enqueue(ok);
        var deadline = DateTime.UtcNow.AddSeconds(3);
        while (processed.Count == 0 && DateTime.UtcNow < deadline)
        {
            await Task.Delay(20);
        }

        queue.Complete();
        await cts.CancelAsync();
        await run;
        Assert.Contains(ok, processed);
    }

    [Fact]
    public async Task Loop_rejects_null_dependencies()
    {
        var queue = new ImportWorkQueue();
        using var cts = new CancellationTokenSource();
        await Assert.ThrowsAsync<ArgumentNullException>(() => ImportParseWorkerLoop.Run(
            null!, (_, _) => Task.CompletedTask, _ => Task.CompletedTask, TimeSpan.FromSeconds(1), cts.Token));
        await Assert.ThrowsAsync<ArgumentNullException>(() => ImportParseWorkerLoop.Run(
            queue, null!, _ => Task.CompletedTask, TimeSpan.FromSeconds(1), cts.Token));
        await Assert.ThrowsAsync<ArgumentNullException>(() => ImportParseWorkerLoop.Run(
            queue, (_, _) => Task.CompletedTask, null!, TimeSpan.FromSeconds(1), cts.Token));
    }

    [Fact]
    public async Task Loop_rethrows_uncancelled_operation_canceled()
    {
        var queue = new ImportWorkQueue();
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(2));
        var run = ImportParseWorkerLoop.Run(
            queue,
            (_, _) => throw new OperationCanceledException(),
            _ => Task.CompletedTask,
            TimeSpan.FromMilliseconds(20),
            cts.Token);
        queue.Enqueue(Guid.NewGuid());
        await Assert.ThrowsAsync<OperationCanceledException>(() => run);
    }

    [Fact]
    public async Task Loop_rethrows_uncancelled_reclaim_canceled()
    {
        var queue = new ImportWorkQueue();
        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(2));
        var run = ImportParseWorkerLoop.Run(
            queue,
            (_, _) => Task.CompletedTask,
            _ => throw new OperationCanceledException(),
            TimeSpan.FromMilliseconds(1),
            cts.Token);
        await Assert.ThrowsAsync<OperationCanceledException>(() => run);
    }

    [Fact]
    public async Task Hosted_worker_processes_reclaims_and_logs_orphans()
    {
        var queue = new ImportWorkQueue();
        var processor = new FakeProcessor();
        var reconciler = new FakeReconciler([new OrphanRawFile("aa/bb/orphan", 4, DateTime.UtcNow)]);
        using var host = new HostBuilder()
            .ConfigureLogging(l => l.ClearProviders())
            .ConfigureServices(s =>
            {
                s.AddSingleton<IImportWorkQueue>(queue);
                s.AddSingleton<IImportFileProcessor>(processor);
                s.AddSingleton<IRawStorageReconciler>(reconciler);
                s.Configure<ImportWorkerOptions>(o => o.ReclaimInterval = TimeSpan.FromMilliseconds(15));
                s.AddHostedService<ImportParseWorker>();
            })
            .Build();
        await host.StartAsync();
        var id = Guid.NewGuid();
        queue.Enqueue(id);
        var deadline = DateTime.UtcNow.AddSeconds(5);
        while ((!processor.Processed.Contains(id) || processor.Reclaims == 0) && DateTime.UtcNow < deadline)
        {
            await Task.Delay(20);
        }

        Assert.Contains(id, processor.Processed);
        Assert.True(processor.Reclaims > 0);
        await host.StopAsync();
    }

    [Fact]
    public async Task Hosted_worker_without_reconciler_and_process_error()
    {
        var queue = new ImportWorkQueue();
        var processor = new FakeProcessor { ThrowOnProcess = new InvalidOperationException("hosted-boom") };
        using var host = new HostBuilder()
            .ConfigureLogging(l => l.ClearProviders())
            .ConfigureServices(s =>
            {
                s.AddSingleton<IImportWorkQueue>(queue);
                s.AddSingleton<IImportFileProcessor>(processor);
                s.Configure<ImportWorkerOptions>(o => o.ReclaimInterval = TimeSpan.FromMilliseconds(15));
                s.AddHostedService<ImportParseWorker>();
            })
            .Build();
        await host.StartAsync();
        queue.Enqueue(Guid.NewGuid());
        var deadline = DateTime.UtcNow.AddSeconds(3);
        while (processor.Reclaims == 0 && DateTime.UtcNow < deadline)
        {
            await Task.Delay(20);
        }

        await host.StopAsync();
        Assert.True(processor.Reclaims > 0);
    }

    [Fact]
    public async Task Hosted_worker_empty_orphan_report()
    {
        var queue = new ImportWorkQueue();
        var processor = new FakeProcessor();
        using var host = new HostBuilder()
            .ConfigureLogging(l => l.ClearProviders())
            .ConfigureServices(s =>
            {
                s.AddSingleton<IImportWorkQueue>(queue);
                s.AddSingleton<IImportFileProcessor>(processor);
                s.AddSingleton<IRawStorageReconciler>(new FakeReconciler([]));
                s.Configure<ImportWorkerOptions>(o => o.ReclaimInterval = TimeSpan.FromMilliseconds(15));
                s.AddHostedService<ImportParseWorker>();
            })
            .Build();
        await host.StartAsync();
        var deadline = DateTime.UtcNow.AddSeconds(3);
        while (processor.Reclaims == 0 && DateTime.UtcNow < deadline)
        {
            await Task.Delay(20);
        }

        await host.StopAsync();
        Assert.True(processor.Reclaims > 0);
    }

    [Fact]
    public void Reconciler_reports_old_unknown_files_only()
    {
        var root = Directory.CreateTempSubdirectory("orphan-raw").FullName;
        try
        {
            using var db = TestDb.Open();
            TestDb.Truncate(db);
            var knownRel = "aa/bb/" + new string('c', 64);
            var knownDir = Path.Combine(root, "aa", "bb");
            Directory.CreateDirectory(knownDir);
            var knownPath = Path.Combine(knownDir, new string('c', 64));
            File.WriteAllBytes(knownPath, [1]);
            db.RawArtifacts.Add(new Persistence.Entities.RawArtifactEntity(
                Guid.NewGuid(), new string('c', 64), 1, knownRel, "QRP"));
            db.SaveChanges();

            var orphanDir = Path.Combine(root, "dd", "ee");
            Directory.CreateDirectory(orphanDir);
            var oldOrphan = Path.Combine(orphanDir, new string('f', 64));
            File.WriteAllBytes(oldOrphan, [2, 3]);
            File.SetLastWriteTimeUtc(oldOrphan, DateTime.UtcNow.AddHours(-3));
            var young = Path.Combine(orphanDir, new string('a', 64));
            File.WriteAllBytes(young, [4]);
            File.SetLastWriteTimeUtc(young, DateTime.UtcNow);
            File.WriteAllBytes(Path.Combine(orphanDir, ".tmp-skip"), [5]);
            File.SetLastWriteTimeUtc(Path.Combine(orphanDir, ".tmp-skip"), DateTime.UtcNow.AddHours(-3));

            var reconciler = new RawStorageReconciler(db, new DataHubOptions { RawStorageRoot = root });
            var orphans = reconciler.ReportOrphans(TimeSpan.FromHours(1));
            Assert.Contains(orphans, o => o.RelativePath.Contains(new string('f', 64), StringComparison.Ordinal));
            Assert.DoesNotContain(orphans, o => o.RelativePath.Contains(new string('c', 64), StringComparison.Ordinal));
            Assert.DoesNotContain(orphans, o => o.RelativePath.Contains(new string('a', 64), StringComparison.Ordinal));
            Assert.DoesNotContain(orphans, o => o.RelativePath.Contains(".tmp-skip", StringComparison.Ordinal));

            var missing = new RawStorageReconciler(db, new DataHubOptions { RawStorageRoot = Path.Combine(root, "nope") });
            Assert.Empty(missing.ReportOrphans(TimeSpan.FromHours(1)));
        }
        finally
        {
            Directory.Delete(root, true);
        }
    }

    private sealed class FakeProcessor : IImportFileProcessor
    {
        public List<Guid> Processed { get; } = [];
        public int Reclaims;
        public Exception? ThrowOnProcess;

        public ImportedFileResult ProcessAcceptedFile(Guid importFileId)
        {
            if (ThrowOnProcess is not null)
            {
                throw ThrowOnProcess;
            }

            Processed.Add(importFileId);
            return new ImportedFileResult(
                Guid.Empty, importFileId, Guid.Empty, Guid.Empty, "", "", false, false, "", "", "", 0, null, null);
        }

        public void ReclaimExpiredProcessingLeases() => Reclaims++;
    }

    private sealed class FakeReconciler(IReadOnlyList<OrphanRawFile> orphans) : IRawStorageReconciler
    {
        public IReadOnlyList<OrphanRawFile> ReportOrphans(TimeSpan minAge) => orphans;
    }
}
