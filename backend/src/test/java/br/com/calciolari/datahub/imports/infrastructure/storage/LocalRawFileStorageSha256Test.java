package br.com.calciolari.datahub.imports.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class LocalRawFileStorageSha256Test {

	@TempDir
	java.nio.file.Path tempDir;

	@Test
	void sha256UnavailableFailsFast() {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		try (MockedStatic<java.security.MessageDigest> digests = mockStatic(java.security.MessageDigest.class)) {
			digests.when(() -> java.security.MessageDigest.getInstance("SHA-256"))
					.thenThrow(new NoSuchAlgorithmException("SHA-256"));
			assertThrows(IllegalStateException.class,
					() -> storage.putIfAbsent(
							new ByteArrayInputStream(new byte[] {1}),
							new RawFileDescriptor("a".repeat(64), 1, "QRP")));
		}
	}
}
