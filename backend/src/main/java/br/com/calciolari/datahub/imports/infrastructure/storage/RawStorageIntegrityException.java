package br.com.calciolari.datahub.imports.infrastructure.storage;

/**
 * Raised when an existing raw artifact disagrees on hash or size (no-clobber integrity).
 */
public class RawStorageIntegrityException extends RuntimeException {

	public RawStorageIntegrityException(String message) {
		super(message);
	}

	public RawStorageIntegrityException(String message, Throwable cause) {
		super(message, cause);
	}
}
