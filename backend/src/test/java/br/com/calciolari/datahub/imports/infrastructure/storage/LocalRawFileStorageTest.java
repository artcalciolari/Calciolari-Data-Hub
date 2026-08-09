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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRawFileStorageTest {

	@TempDir
	Path tempDir;

	@Test
	void putIfAbsentIsIdempotentAndDetectsCorruption() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = "qrp-bytes".getBytes(StandardCharsets.UTF_8);
		String sha = sha256(payload);
		RawFileDescriptor descriptor = new RawFileDescriptor(sha, payload.length, "QRP");

		StoredRawFile first = storage.putIfAbsent(new ByteArrayInputStream(payload), descriptor);
		assertTrue(first.created());
		assertTrue(storage.exists(first.storageKey()));

		StoredRawFile second = storage.putIfAbsent(new ByteArrayInputStream(payload), descriptor);
		assertFalse(second.created());
		assertEquals(first.storageKey(), second.storageKey());

		Path target = tempDir.resolve(first.storageKey());
		Files.writeString(target, "tampered");
		assertThrows(RawStorageIntegrityException.class,
				() -> storage.putIfAbsent(new ByteArrayInputStream(payload), descriptor));
		assertThrows(RawStorageIntegrityException.class,
				() -> storage.openVerified(first.storageKey(), sha, payload.length));
	}

	@Test
	void concurrentPutIfAbsentSingleWinner() throws Exception {
		LocalRawFileStorage storage = new LocalRawFileStorage(tempDir.toString());
		byte[] payload = Files.readAllBytes(
				Path.of("src/test/resources/fixtures/qrp/fixture-a.qrp"));
		String sha = sha256(payload);
		RawFileDescriptor descriptor = new RawFileDescriptor(sha, payload.length, "QRP");

		ExecutorService pool = Executors.newFixedThreadPool(8);
		try {
			Callable<StoredRawFile> task = () -> storage.putIfAbsent(new ByteArrayInputStream(payload), descriptor);
			@SuppressWarnings("unchecked")
			Future<StoredRawFile>[] futures = new Future[8];
			for (int i = 0; i < futures.length; i++) {
				futures[i] = pool.submit(task);
			}
			int created = 0;
			for (Future<StoredRawFile> future : futures) {
				if (future.get().created()) {
					created++;
				}
			}
			assertEquals(1, created);
			assertTrue(storage.exists(LocalRawFileStorage.storageKeyFor(sha)));
		}
		finally {
			pool.shutdownNow();
		}
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}
