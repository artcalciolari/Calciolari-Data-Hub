package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "import_file")
public class ImportFileEntity {

	@Id
	private UUID id;

	@Column(name = "import_job_id", nullable = false)
	private UUID importJobId;

	@Column(name = "raw_artifact_id", nullable = false)
	private UUID rawArtifactId;

	@Column(name = "parse_attempt_id")
	private UUID parseAttemptId;

	@Column(name = "original_filename", nullable = false)
	private String originalFilename;

	@Column(nullable = false)
	private String source;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "filename_hints", columnDefinition = "jsonb")
	private String filenameHints;

	@Column(nullable = false)
	private String status;

	@Column(nullable = false)
	private boolean deduplicated;

	@Column(name = "duplicate_of_import_file_id")
	private UUID duplicateOfImportFileId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected ImportFileEntity() {
	}

	public ImportFileEntity(
			UUID id,
			UUID importJobId,
			UUID rawArtifactId,
			String originalFilename,
			String source,
			String status) {
		this.id = id;
		this.importJobId = importJobId;
		this.rawArtifactId = rawArtifactId;
		this.originalFilename = originalFilename;
		this.source = source;
		this.status = status;
		this.deduplicated = false;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getImportJobId() {
		return importJobId;
	}

	public UUID getRawArtifactId() {
		return rawArtifactId;
	}

	public UUID getParseAttemptId() {
		return parseAttemptId;
	}

	public void setParseAttemptId(UUID parseAttemptId) {
		this.parseAttemptId = parseAttemptId;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getSource() {
		return source;
	}

	public String getFilenameHints() {
		return filenameHints;
	}

	public void setFilenameHints(String filenameHints) {
		this.filenameHints = filenameHints;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public boolean isDeduplicated() {
		return deduplicated;
	}

	public void setDeduplicated(boolean deduplicated) {
		this.deduplicated = deduplicated;
	}

	public UUID getDuplicateOfImportFileId() {
		return duplicateOfImportFileId;
	}

	public void setDuplicateOfImportFileId(UUID duplicateOfImportFileId) {
		this.duplicateOfImportFileId = duplicateOfImportFileId;
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
