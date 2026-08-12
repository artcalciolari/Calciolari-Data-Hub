namespace Calciolari.DataHub.Persistence.Entities;

public sealed class RawArtifactEntity
{
    public Guid Id { get; set; }
    public string Sha256 { get; set; } = string.Empty;
    public long ByteSize { get; set; }
    public string StorageKey { get; set; } = string.Empty;
    public string? DetectedType { get; set; }
    public DateTimeOffset CreatedAt { get; set; }

    public RawArtifactEntity()
    {
    }

    public RawArtifactEntity(Guid id, string sha256, long byteSize, string storageKey, string? detectedType)
    {
        Id = id;
        Sha256 = sha256;
        ByteSize = byteSize;
        StorageKey = storageKey;
        DetectedType = detectedType;
        CreatedAt = DateTimeOffset.UtcNow;
    }
}
