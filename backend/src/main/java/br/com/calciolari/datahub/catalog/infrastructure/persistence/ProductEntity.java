package br.com.calciolari.datahub.catalog.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product")
public class ProductEntity {

	@Id
	private UUID id;

	@Column(name = "external_source", nullable = false)
	private String externalSource;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@Column(nullable = false)
	private String name;

	private String unit;

	@Column(name = "first_seen_parse_attempt_id", nullable = false)
	private UUID firstSeenParseAttemptId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductEntity() {
	}

	public ProductEntity(
			UUID id,
			String externalSource,
			String externalId,
			String name,
			UUID firstSeenParseAttemptId) {
		this.id = id;
		this.externalSource = externalSource;
		this.externalId = externalId;
		this.name = name;
		this.firstSeenParseAttemptId = firstSeenParseAttemptId;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getExternalSource() {
		return externalSource;
	}

	public String getExternalId() {
		return externalId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		this.updatedAt = Instant.now();
	}

	public UUID getFirstSeenParseAttemptId() {
		return firstSeenParseAttemptId;
	}
}
