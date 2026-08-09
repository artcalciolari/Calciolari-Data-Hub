package br.com.calciolari.datahub.imports.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Filesystem raw store. Keys look like {@code ab/cd/<sha256>}; never derived from filenames.
 * {@link #putIfAbsent} is idempotent and never overwrites divergent bytes.
 */
@Component
public class LocalRawFileStorage implements RawFileStorage {

	private final Path root;

	public LocalRawFileStorage(@Value("${datahub.raw-storage.root}") String root) {
		this.root = Path.of(Objects.requireNonNull(root, "root")).toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	@Override
	public StoredRawFile putIfAbsent(InputStream bytes, RawFileDescriptor descriptor) {
		Objects.requireNonNull(bytes, "bytes");
		Objects.requireNonNull(descriptor, "descriptor");
		ensureRoot();

		String sha = descriptor.sha256().toLowerCase(Locale.ROOT);
		String storageKey = storageKeyFor(sha);
		Path target = resolveKey(storageKey);

		synchronized (lockFor(sha)) {
			if (Files.exists(target)) {
				verifyExisting(target, descriptor);
				return new StoredRawFile(storageKey, sha, descriptor.byteSize(), false);
			}

			try {
				Files.createDirectories(target.getParent());
				Path temp = target.getParent().resolve(
						"." + sha + "." + UUID.randomUUID() + ".tmp");
				long written = 0L;
				MessageDigest digest = sha256Digest();
				try (InputStream in = new DigestInputStream(bytes, digest);
						OutputStream out = Files.newOutputStream(temp,
								StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
					byte[] buffer = new byte[8192];
					int read;
					while ((read = in.read(buffer)) >= 0) {
						out.write(buffer, 0, read);
						written += read;
					}
				}
				String actualSha = HexFormat.of().formatHex(digest.digest());
				if (!sha.equalsIgnoreCase(actualSha)) {
					Files.deleteIfExists(temp);
					throw new RawStorageIntegrityException(
							"stream SHA-256 mismatch: expected " + sha + " got " + actualSha);
				}
				if (written != descriptor.byteSize()) {
					Files.deleteIfExists(temp);
					throw new RawStorageIntegrityException(
							"stream size mismatch: expected " + descriptor.byteSize() + " got " + written);
				}

				try {
					Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
				}
				catch (AtomicMoveNotSupportedException ex) {
					try {
						Files.move(temp, target);
					}
					catch (java.nio.file.FileAlreadyExistsException race) {
						Files.deleteIfExists(temp);
						verifyExisting(target, descriptor);
						return new StoredRawFile(storageKey, sha, descriptor.byteSize(), false);
					}
				}
				catch (java.nio.file.FileAlreadyExistsException ex) {
					Files.deleteIfExists(temp);
					verifyExisting(target, descriptor);
					return new StoredRawFile(storageKey, sha, descriptor.byteSize(), false);
				}

				return new StoredRawFile(storageKey, sha, descriptor.byteSize(), true);
			}
			catch (IOException ex) {
				throw new UncheckedIOException("failed to store raw artifact " + sha, ex);
			}
		}
	}

	private static Object lockFor(String sha) {
		return sha.intern();
	}

	@Override
	public InputStream openVerified(String storageKey, String expectedSha256, long expectedSize) {
		Path path = resolveKey(storageKey);
		if (!Files.isRegularFile(path)) {
			throw new RawStorageIntegrityException("missing raw artifact: " + storageKey);
		}
		try {
			long size = Files.size(path);
			if (size != expectedSize) {
				throw new RawStorageIntegrityException(
						"size mismatch for " + storageKey + ": expected " + expectedSize + " got " + size);
			}
			MessageDigest digest = sha256Digest();
			try (InputStream in = new DigestInputStream(Files.newInputStream(path), digest)) {
				in.transferTo(OutputStream.nullOutputStream());
			}
			String actual = HexFormat.of().formatHex(digest.digest());
			if (!expectedSha256.equalsIgnoreCase(actual)) {
				throw new RawStorageIntegrityException(
						"hash mismatch for " + storageKey + ": expected " + expectedSha256 + " got " + actual);
			}
			return Files.newInputStream(path);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("failed to open raw artifact " + storageKey, ex);
		}
	}

	@Override
	public boolean exists(String storageKey) {
		return Files.isRegularFile(resolveKey(storageKey));
	}

	public static String storageKeyFor(String sha256) {
		String sha = sha256.toLowerCase(Locale.ROOT);
		if (sha.length() != 64) {
			throw new IllegalArgumentException("sha256 must be 64 hex chars");
		}
		return sha.substring(0, 2) + "/" + sha.substring(2, 4) + "/" + sha;
	}

	private void verifyExisting(Path target, RawFileDescriptor descriptor) {
		try {
			long size = Files.size(target);
			if (size != descriptor.byteSize()) {
				throw new RawStorageIntegrityException(
						"existing artifact size mismatch for " + descriptor.sha256()
								+ ": expected " + descriptor.byteSize() + " got " + size);
			}
			MessageDigest digest = sha256Digest();
			try (InputStream in = new DigestInputStream(Files.newInputStream(target), digest)) {
				in.transferTo(OutputStream.nullOutputStream());
			}
			String actual = HexFormat.of().formatHex(digest.digest());
			if (!descriptor.sha256().equalsIgnoreCase(actual)) {
				throw new RawStorageIntegrityException(
						"existing artifact hash mismatch for key of " + descriptor.sha256()
								+ ": got " + actual);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private Path resolveKey(String storageKey) {
		Path resolved = root.resolve(storageKey).normalize();
		if (!resolved.startsWith(root)) {
			throw new SecurityException("storage key escapes root: " + storageKey);
		}
		return resolved;
	}

	private void ensureRoot() {
		try {
			Files.createDirectories(root);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
