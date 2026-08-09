package br.com.calciolari.datahub.imports.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class LocalRawFileStorageBranchTest {

	@TempDir
	Path tempDir;

	@Test
	void atomicMoveFallbackAndFileAlreadyExists() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "atomic".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);

		AtomicBoolean firstMove = new AtomicBoolean(true);
		try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			files.when(() -> Files.move(any(Path.class), any(Path.class), eq(StandardCopyOption.ATOMIC_MOVE)))
					.thenAnswer(inv -> {
						if (firstMove.getAndSet(false)) {
							throw new AtomicMoveNotSupportedException(
									inv.getArgument(0).toString(),
									inv.getArgument(1).toString(),
									"no atomic");
						}
						return inv.callRealMethod();
					});
			StoredRawFile created = storage.putIfAbsent(
					new ByteArrayInputStream(payload), new RawFileDescriptor(sha, payload.length, "QRP"));
			assertTrue(created.created());
		}

		// Second put hits exists path.
		StoredRawFile again = storage.putIfAbsent(
				new ByteArrayInputStream(payload), new RawFileDescriptor(sha, payload.length, "QRP"));
		assertFalse(again.created());
	}

	@Test
	void fileAlreadyExistsDuringAtomicMove() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "race-move".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		String key = LocalRawFileStorage.storageKeyFor(sha);
		Path target = tempDir.resolve(key);
		Files.createDirectories(target.getParent());
		Files.write(target, payload);

		try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			files.when(() -> Files.exists(eq(target))).thenReturn(false);
			files.when(() -> Files.move(any(Path.class), eq(target), eq(StandardCopyOption.ATOMIC_MOVE)))
					.thenThrow(new java.nio.file.FileAlreadyExistsException(target.toString()));
			StoredRawFile stored = storage.putIfAbsent(
					new ByteArrayInputStream(payload), new RawFileDescriptor(sha, payload.length, "QRP"));
			assertFalse(stored.created());
		}
	}

	@Test
	void existingHashMismatchAndOpenHashMismatch() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		String key = LocalRawFileStorage.storageKeyFor(sha);
		Path target = tempDir.resolve(key);
		Files.createDirectories(target.getParent());
		Files.write(target, "other".getBytes(StandardCharsets.UTF_8));

		assertThrows(RawStorageIntegrityException.class,
				() -> storage.putIfAbsent(new ByteArrayInputStream(payload),
						new RawFileDescriptor(sha, "other".getBytes(StandardCharsets.UTF_8).length, "QRP")));

		Files.write(target, payload);
		assertThrows(RawStorageIntegrityException.class,
				() -> storage.openVerified(key, "0".repeat(64), payload.length));
	}

	@Test
	void ensureRootFailure() throws Exception {
		Path blocked = tempDir.resolve("blocked-as-file");
		Files.writeString(blocked, "x");
		LocalRawFileStorage storage = new LocalRawFileStorage(blocked.resolve("child").toString());
		assertThrows(java.io.UncheckedIOException.class,
				() -> storage.putIfAbsent(new ByteArrayInputStream(new byte[] {1}),
						new RawFileDescriptor(sha256(new byte[] {1}), 1, "QRP")));
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
