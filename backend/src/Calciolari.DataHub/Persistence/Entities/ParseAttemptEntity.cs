namespace Calciolari.DataHub.Persistence.Entities;

public sealed class ParseAttemptEntity
{
    public Guid Id { get; set; }
    public Guid RawArtifactId { get; set; }
    public string ParserName { get; set; } = string.Empty;
    public string ParserVersion { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public int? RecordsFound { get; set; }
    public int AttemptCount { get; set; }
    public DateTimeOffset? LeaseUntil { get; set; }
    public string? LeaseOwner { get; set; }
    public long LeaseGeneration { get; set; }
    public DateTimeOffset? StartedAt { get; set; }
    public DateTimeOffset? CompletedAt { get; set; }
    public string? ErrorSummary { get; set; }

    public ParseAttemptEntity()
    {
    }

    public ParseAttemptEntity(
        Guid id,
        Guid rawArtifactId,
        string parserName,
        string parserVersion,
        string status,
        int attemptCount)
    {
        Id = id;
        RawArtifactId = rawArtifactId;
        ParserName = parserName;
        ParserVersion = parserVersion;
        Status = status;
        AttemptCount = attemptCount;
        LeaseGeneration = 0;
    }
}
