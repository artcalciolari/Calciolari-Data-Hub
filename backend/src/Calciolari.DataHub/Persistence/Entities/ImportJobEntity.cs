namespace Calciolari.DataHub.Persistence.Entities;

public sealed class ImportJobEntity
{
    public Guid Id { get; set; }
    public string Status { get; set; } = string.Empty;
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }

    public ImportJobEntity()
    {
    }

    public ImportJobEntity(Guid id, string status)
    {
        Id = id;
        Status = status;
        CreatedAt = DateTimeOffset.UtcNow;
    }
}
