namespace Calciolari.DataHub.Imports.Infrastructure.Storage;

/// <summary>
/// Descriptor for an immutable raw artifact. Storage keys are server-generated;
/// never derived from originalFilename.
/// </summary>
public sealed record RawFileDescriptor(string Sha256, long ByteSize, string? DetectedType)
{
    public static RawFileDescriptor Create(string sha256, long byteSize, string? detectedType)
    {
        ArgumentNullException.ThrowIfNull(sha256);
        if (sha256.Length != 64)
        {
            throw new ArgumentException("sha256 must be 64 hex chars", nameof(sha256));
        }

        if (byteSize < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(byteSize), "byteSize must be >= 0");
        }

        return new RawFileDescriptor(sha256, byteSize, detectedType);
    }
}
