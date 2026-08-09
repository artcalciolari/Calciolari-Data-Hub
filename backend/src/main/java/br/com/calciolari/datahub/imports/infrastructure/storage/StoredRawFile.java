package br.com.calciolari.datahub.imports.infrastructure.storage;

import java.util.Objects;

public record StoredRawFile(
		String storageKey,
		String sha256,
		long byteSize,
		boolean created
) {
	public StoredRawFile {
		Objects.requireNonNull(storageKey, "storageKey");
		Objects.requireNonNull(sha256, "sha256");
	}
}
