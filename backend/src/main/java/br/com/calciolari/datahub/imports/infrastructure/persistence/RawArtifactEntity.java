package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "raw_artifact")
public class RawArtifactEntity {

	@Id
	private UUID id;

	@Column(nullable = false, length = 64, unique = true)
	private String sha256;

	@Column(name = "byte_size", nullable = false)
	private long byteSize;

	@Column(name = "storage_key", nullable = false, unique = true)
	private String storageKey;

	@Column(name = "detected_type")
	private String detectedType;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected RawArtifactEntity() {
	}

	public RawArtifactEntity(UUID id, String sha256, long byteSize, String storageKey, String detectedType) {
		this.id = id;
		this.sha256 = sha256;
		this.byteSize = byteSize;
		this.storageKey = storageKey;
		this.detectedType = detectedType;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getSha256() {
		return sha256;
	}

	public long getByteSize() {
		return byteSize;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getDetectedType() {
		return detectedType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
