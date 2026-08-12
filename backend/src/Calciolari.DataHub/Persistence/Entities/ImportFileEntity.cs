namespace Calciolari.DataHub.Persistence.Entities;

public sealed class ImportFileEntity
{
    public Guid Id { get; set; }
    public Guid ImportJobId { get; set; }
    public Guid RawArtifactId { get; set; }
    public Guid? ParseAttemptId { get; set; }
    public string OriginalFilename { get; set; } = string.Empty;
    public string Source { get; set; } = string.Empty;
    public string? FilenameHints { get; set; }
    public string Status { get; set; } = string.Empty;
    public bool Deduplicated { get; set; }
    public Guid? DuplicateOfImportFileId { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }

    public ImportFileEntity()
    {
    }

    public ImportFileEntity(
        Guid id,
        Guid importJobId,
        Guid rawArtifactId,
        string originalFilename,
        string source,
        string status)
    {
        Id = id;
        ImportJobId = importJobId;
        RawArtifactId = rawArtifactId;
        OriginalFilename = originalFilename;
        Source = source;
        Status = status;
        Deduplicated = false;
        CreatedAt = DateTimeOffset.UtcNow;
    }
}
