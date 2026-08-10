package br.com.calciolari.datahub.imports.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class LocalRawFileStorageIoTest {

	@TempDir
	Path tempDir;

	@Test
	void storeIOExceptionWraps() {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "io".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			files.when(() -> Files.newOutputStream(any(Path.class), eq(StandardOpenOption.CREATE_NEW),
					eq(StandardOpenOption.WRITE)))
					.thenThrow(new IOException("write failed"));
			assertThrows(UncheckedIOException.class,
					() -> storage.putIfAbsent(new ByteArrayInputStream(payload),
							new RawFileDescriptor(sha, payload.length, "QRP")));
		}
	}

	@Test
	void openVerifiedIOExceptionWraps() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "open".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		StoredRawFile stored = storage.putIfAbsent(
				new ByteArrayInputStream(payload), new RawFileDescriptor(sha, payload.length, "QRP"));
		Path path = tempDir.resolve(stored.storageKey());
		try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			files.when(() -> Files.newInputStream(path)).thenThrow(new IOException("no read"));
			assertThrows(UncheckedIOException.class,
					() -> storage.openVerified(stored.storageKey(), sha, payload.length));
		}
	}

	@Test
	void verifyExistingIOExceptionWraps() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "verify".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		String key = LocalRawFileStorage.storageKeyFor(sha);
		Path target = tempDir.resolve(key);
		Files.createDirectories(target.getParent());
		Files.write(target, payload);

		try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			files.when(() -> Files.size(target)).thenThrow(new IOException("size"));
			assertThrows(UncheckedIOException.class,
					() -> storage.putIfAbsent(new ByteArrayInputStream(payload),
							new RawFileDescriptor(sha, payload.length, "QRP")));
		}
	}

	@Test
	void atomicMoveFallbackRaceWithExistingTarget() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "race2".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		String key = LocalRawFileStorage.storageKeyFor(sha);
		Path target = tempDir.resolve(key);
		Files.createDirectories(target.getParent());
		Files.write(target, payload);

		AtomicBoolean atomicAttempted = new AtomicBoolean(true);
		try (MockedStatic<Files> files = mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
			files.when(() -> Files.exists(eq(target))).thenReturn(false);
			files.when(() -> Files.move(any(Path.class), eq(target), eq(StandardCopyOption.ATOMIC_MOVE)))
					.thenThrow(new AtomicMoveNotSupportedException("a", "b", "no atomic"));
			files.when(() -> Files.move(any(Path.class), eq(target)))
					.thenThrow(new java.nio.file.FileAlreadyExistsException(target.toString()));
			storage.putIfAbsent(new ByteArrayInputStream(payload),
					new RawFileDescriptor(sha, payload.length, "QRP"));
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
