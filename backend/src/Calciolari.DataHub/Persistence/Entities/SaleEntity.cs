namespace Calciolari.DataHub.Persistence.Entities;

public sealed class SaleEntity
{
    public Guid Id { get; set; }
    public string ExternalSource { get; set; } = string.Empty;
    public string ExternalSaleId { get; set; } = string.Empty;
    public DateTime? OccurredAt { get; set; }
    public Guid FirstSeenParseAttemptId { get; set; }
    public DateTimeOffset CreatedAt { get; set; }

    public SaleEntity()
    {
    }

    public SaleEntity(
        Guid id,
        string externalSource,
        string externalSaleId,
        DateTime? occurredAt,
        Guid firstSeenParseAttemptId)
    {
        Id = id;
        ExternalSource = externalSource;
        ExternalSaleId = externalSaleId;
        OccurredAt = occurredAt;
        FirstSeenParseAttemptId = firstSeenParseAttemptId;
        CreatedAt = DateTimeOffset.UtcNow;
    }
}
