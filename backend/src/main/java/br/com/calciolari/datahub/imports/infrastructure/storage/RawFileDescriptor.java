package br.com.calciolari.datahub.imports.infrastructure.storage;

import java.util.Objects;

/**
 * Descriptor for an immutable raw artifact. Storage keys are server-generated;
 * never derived from {@code originalFilename}.
 */
public record RawFileDescriptor(
		String sha256,
		long byteSize,
		String detectedType
) {
	public RawFileDescriptor {
		Objects.requireNonNull(sha256, "sha256");
		if (sha256.length() != 64) {
			throw new IllegalArgumentException("sha256 must be 64 hex chars");
		}
		if (byteSize < 0) {
			throw new IllegalArgumentException("byteSize must be >= 0");
		}
	}
}
