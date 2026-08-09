package br.com.calciolari.datahub.imports.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRawFileStorageExtraTest {

	@TempDir
	Path tempDir;

	@Test
	void rejectsMismatchedStreamHashAndSize() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "abc".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);

		assertThrows(RawStorageIntegrityException.class,
				() -> storage.putIfAbsent(new ByteArrayInputStream(payload),
						new RawFileDescriptor("0".repeat(64), payload.length, "QRP")));

		assertThrows(RawStorageIntegrityException.class,
				() -> storage.putIfAbsent(new ByteArrayInputStream(payload),
						new RawFileDescriptor(sha, payload.length + 1, "QRP")));
	}

	@Test
	void openVerifiedMissingAndSizeMismatch() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		StoredRawFile stored = storage.putIfAbsent(
				new ByteArrayInputStream(payload), new RawFileDescriptor(sha, payload.length, "QRP"));

		assertThrows(RawStorageIntegrityException.class,
				() -> storage.openVerified("aa/bb/" + "f".repeat(64), sha, 1));
		assertThrows(RawStorageIntegrityException.class,
				() -> storage.openVerified(stored.storageKey(), sha, payload.length + 9));

		try (var in = storage.openVerified(stored.storageKey(), sha.toUpperCase(), payload.length)) {
			assertEquals(payload.length, in.readAllBytes().length);
		}
		assertFalse(storage.exists("00/00/" + "a".repeat(64)));
		assertTrue(storage.exists(stored.storageKey()));
	}

	@Test
	void storageKeyValidationAndPathEscape() {
		assertThrows(IllegalArgumentException.class, () -> LocalRawFileStorage.storageKeyFor("short"));
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		assertThrows(SecurityException.class, () -> storage.exists("../escape"));
	}

	@Test
	void existingSizeMismatchOnPut() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "same-key".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		storage.putIfAbsent(new ByteArrayInputStream(payload), new RawFileDescriptor(sha, payload.length, "QRP"));
		assertThrows(RawStorageIntegrityException.class,
				() -> storage.putIfAbsent(new ByteArrayInputStream(payload),
						new RawFileDescriptor(sha, payload.length + 1, "QRP")));
	}

	@Test
	void rawFileDescriptorAndIntegrityException() {
		RawFileDescriptor d = new RawFileDescriptor("A".repeat(64), 2, null);
		assertEquals("A".repeat(64), d.sha256());
		assertEquals(2, d.byteSize());
		assertEquals(null, d.detectedType());
		assertThrows(IllegalArgumentException.class,
				() -> new RawFileDescriptor("a".repeat(64), -1, "QRP"));
		assertThrows(IllegalArgumentException.class,
				() -> new RawFileDescriptor("ab", 1, "QRP"));

		RawStorageIntegrityException plain = new RawStorageIntegrityException("x");
		assertEquals("x", plain.getMessage());
		RawStorageIntegrityException caused = new RawStorageIntegrityException("y", new RuntimeException());
		assertEquals("y", caused.getMessage());
	}

	@Test
	void nullArgumentsOnPut() {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		assertThrows(NullPointerException.class,
				() -> storage.putIfAbsent(null, new RawFileDescriptor("a".repeat(64), 0, "QRP")));
		assertThrows(NullPointerException.class,
				() -> storage.putIfAbsent(new ByteArrayInputStream(new byte[0]), null));
	}

	@Test
	void rawStoragePropertiesDefaults() {
		RawStorageProperties props = new RawStorageProperties();
		assertEquals("./data/raw-storage", props.getRoot());
		props.setRoot("/tmp/x");
		assertEquals("/tmp/x", props.getRoot());
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
