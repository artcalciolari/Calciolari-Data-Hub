namespace Calciolari.DataHub.Persistence.Entities;

public sealed class ProductEntity
{
    public Guid Id { get; set; }
    public string ExternalSource { get; set; } = string.Empty;
    public string ExternalId { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string? Unit { get; set; }
    public Guid FirstSeenParseAttemptId { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }

    public ProductEntity()
    {
    }

    public ProductEntity(
        Guid id,
        string externalSource,
        string externalId,
        string name,
        Guid firstSeenParseAttemptId)
    {
        Id = id;
        ExternalSource = externalSource;
        ExternalId = externalId;
        Name = name;
        FirstSeenParseAttemptId = firstSeenParseAttemptId;
        var now = DateTimeOffset.UtcNow;
        CreatedAt = now;
        UpdatedAt = now;
    }

    public void SetName(string name)
    {
        Name = name;
        UpdatedAt = DateTimeOffset.UtcNow;
    }
}
