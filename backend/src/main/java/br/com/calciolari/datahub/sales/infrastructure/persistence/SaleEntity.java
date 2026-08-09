package br.com.calciolari.datahub.sales.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sale")
public class SaleEntity {

	@Id
	private UUID id;

	@Column(name = "external_source", nullable = false)
	private String externalSource;

	@Column(name = "external_sale_id", nullable = false)
	private String externalSaleId;

	@Column(name = "occurred_at")
	private LocalDateTime occurredAt;

	@Column(name = "first_seen_parse_attempt_id", nullable = false)
	private UUID firstSeenParseAttemptId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SaleEntity() {
	}

	public SaleEntity(
			UUID id,
			String externalSource,
			String externalSaleId,
			LocalDateTime occurredAt,
			UUID firstSeenParseAttemptId) {
		this.id = id;
		this.externalSource = externalSource;
		this.externalSaleId = externalSaleId;
		this.occurredAt = occurredAt;
		this.firstSeenParseAttemptId = firstSeenParseAttemptId;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getExternalSource() {
		return externalSource;
	}

	public String getExternalSaleId() {
		return externalSaleId;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public UUID getFirstSeenParseAttemptId() {
		return firstSeenParseAttemptId;
	}
}
