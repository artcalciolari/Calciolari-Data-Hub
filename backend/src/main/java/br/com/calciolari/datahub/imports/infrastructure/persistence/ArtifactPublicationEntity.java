package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "artifact_publication")
public class ArtifactPublicationEntity {

	@Id
	@Column(name = "raw_artifact_id")
	private UUID rawArtifactId;

	@Column(name = "active_parse_attempt_id", nullable = false, unique = true)
	private UUID activeParseAttemptId;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	protected ArtifactPublicationEntity() {
	}

	public ArtifactPublicationEntity(UUID rawArtifactId, UUID activeParseAttemptId) {
		this.rawArtifactId = rawArtifactId;
		this.activeParseAttemptId = activeParseAttemptId;
		this.publishedAt = Instant.now();
	}

	public UUID getRawArtifactId() {
		return rawArtifactId;
	}

	public UUID getActiveParseAttemptId() {
		return activeParseAttemptId;
	}

	public void setActiveParseAttemptId(UUID activeParseAttemptId) {
		this.activeParseAttemptId = activeParseAttemptId;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}
}
