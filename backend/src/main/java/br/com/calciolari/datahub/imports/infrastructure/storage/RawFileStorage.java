package br.com.calciolari.datahub.imports.infrastructure.storage;

import java.io.InputStream;

/**
 * Immutable raw artifact store. {@code putIfAbsent} must never clobber existing
 * bytes; hash/size divergence is an integrity failure.
 */
public interface RawFileStorage {

	StoredRawFile putIfAbsent(InputStream bytes, RawFileDescriptor descriptor);

	InputStream openVerified(String storageKey, String expectedSha256, long expectedSize);

	boolean exists(String storageKey);
}
