package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parse_attempt")
public class ParseAttemptEntity {

	@Id
	private UUID id;

	@Column(name = "raw_artifact_id", nullable = false)
	private UUID rawArtifactId;

	@Column(name = "parser_name", nullable = false)
	private String parserName;

	@Column(name = "parser_version", nullable = false)
	private String parserVersion;

	@Column(nullable = false)
	private String status;

	@Column(name = "records_found")
	private Integer recordsFound;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "lease_until")
	private Instant leaseUntil;

	@Column(name = "lease_owner")
	private String leaseOwner;

	@Column(name = "lease_generation", nullable = false)
	private long leaseGeneration;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "error_summary")
	private String errorSummary;

	protected ParseAttemptEntity() {
	}

	public ParseAttemptEntity(
			UUID id,
			UUID rawArtifactId,
			String parserName,
			String parserVersion,
			String status,
			int attemptCount) {
		this.id = id;
		this.rawArtifactId = rawArtifactId;
		this.parserName = parserName;
		this.parserVersion = parserVersion;
		this.status = status;
		this.attemptCount = attemptCount;
		this.leaseGeneration = 0L;
	}

	public UUID getId() {
		return id;
	}

	public UUID getRawArtifactId() {
		return rawArtifactId;
	}

	public String getParserName() {
		return parserName;
	}

	public String getParserVersion() {
		return parserVersion;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getRecordsFound() {
		return recordsFound;
	}

	public void setRecordsFound(Integer recordsFound) {
		this.recordsFound = recordsFound;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public Instant getLeaseUntil() {
		return leaseUntil;
	}

	public void setLeaseUntil(Instant leaseUntil) {
		this.leaseUntil = leaseUntil;
	}

	public String getLeaseOwner() {
		return leaseOwner;
	}

	public void setLeaseOwner(String leaseOwner) {
		this.leaseOwner = leaseOwner;
	}

	public long getLeaseGeneration() {
		return leaseGeneration;
	}

	public void setLeaseGeneration(long leaseGeneration) {
		this.leaseGeneration = leaseGeneration;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public String getErrorSummary() {
		return errorSummary;
	}

	public void setErrorSummary(String errorSummary) {
		this.errorSummary = errorSummary;
	}
}
