package br.com.calciolari.datahub.imports.domain.parser;

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Bytes + optional non-authoritative metadata for {@link ImportParser}.
 * {@code originalFilename} is never identity.
 */
public record ParserInput(
		InputStream content,
		long contentLength,
		String originalFilename,
		String detectedType
) {
	public ParserInput {
		Objects.requireNonNull(content, "content");
		if (contentLength < 0) {
			throw new IllegalArgumentException("contentLength must be >= 0");
		}
	}

	public Optional<String> originalFilenameOptional() {
		return Optional.ofNullable(originalFilename).filter(name -> !name.isBlank());
	}
}
