namespace Calciolari.DataHub.Imports.Application;

public sealed class ImportMetrics
{
    public const string Completed = "imports.completed";
    public const string Duplicates = "imports.duplicates";
    public const string Warnings = "imports.warnings";
    public const string Failures = "imports.failures";
    public const string DurationMs = "imports.duration.ms";
    public const string RawStorageBytes = "raw.storage.bytes";

    private long _completed;
    private long _duplicates;
    private long _warnings;
    private long _failures;
    private long _durationMs;
    private long _rawStorageBytes;

    public void RecordJobCompleted(string status)
    {
        if (status is "SUCCEEDED" or "PARTIAL_SUCCESS" or "FAILED")
        {
            Interlocked.Increment(ref _completed);
        }
    }

    public void RecordFile(string status, bool deduplicated, long durationMs)
    {
        if (deduplicated)
        {
            Interlocked.Increment(ref _duplicates);
        }

        if (status is "WARNING")
        {
            Interlocked.Increment(ref _warnings);
        }
        else if (status is "INVALID" or "FAILED")
        {
            Interlocked.Increment(ref _failures);
        }

        if (durationMs > 0)
        {
            Interlocked.Add(ref _durationMs, durationMs);
        }
    }

    public void AddRawBytes(long bytes)
    {
        if (bytes > 0)
        {
            Interlocked.Add(ref _rawStorageBytes, bytes);
        }
    }

    public object Snapshot()
    {
        var measurements = new (string Name, long Value)[]
        {
            (Completed, Interlocked.Read(ref _completed)),
            (Duplicates, Interlocked.Read(ref _duplicates)),
            (Warnings, Interlocked.Read(ref _warnings)),
            (Failures, Interlocked.Read(ref _failures)),
            (DurationMs, Interlocked.Read(ref _durationMs)),
            (RawStorageBytes, Interlocked.Read(ref _rawStorageBytes))
        };
        return new
        {
            names = measurements.Select(m => m.Name).ToArray(),
            measurements = measurements.Select(m => new { name = m.Name, value = m.Value }).ToArray()
        };
    }
}
