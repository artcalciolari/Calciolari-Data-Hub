namespace Calciolari.DataHub.Persistence.Entities;

public sealed class ArtifactPublicationEntity
{
    public Guid RawArtifactId { get; set; }
    public Guid ActiveParseAttemptId { get; set; }
    public DateTimeOffset PublishedAt { get; set; }

    public ArtifactPublicationEntity()
    {
    }

    public ArtifactPublicationEntity(Guid rawArtifactId, Guid activeParseAttemptId)
    {
        RawArtifactId = rawArtifactId;
        ActiveParseAttemptId = activeParseAttemptId;
        PublishedAt = DateTimeOffset.UtcNow;
    }
}
