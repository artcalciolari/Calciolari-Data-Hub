package br.com.calciolari.datahub.imports.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRawFileStorageRaceTest {

	@TempDir
	Path tempDir;

	@Test
	void putIfAbsentWhenTargetAlreadyExists() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "race".getBytes();
		String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
		String key = LocalRawFileStorage.storageKeyFor(sha);
		Path target = tempDir.resolve(key);
		Files.createDirectories(target.getParent());
		Files.write(target, payload);

		StoredRawFile stored = storage.putIfAbsent(
				new ByteArrayInputStream(payload), new RawFileDescriptor(sha, payload.length, "QRP"));
		assertFalse(stored.created());
	}
}
