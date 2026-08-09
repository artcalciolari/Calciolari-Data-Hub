package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "import_job")
public class ImportJobEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected ImportJobEntity() {
	}

	public ImportJobEntity(UUID id, String status) {
		this.id = id;
		this.status = status;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}
}
