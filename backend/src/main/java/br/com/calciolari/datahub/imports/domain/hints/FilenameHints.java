package br.com.calciolari.datahub.imports.domain.hints;

import java.util.Objects;
import java.util.Optional;

/**
 * Non-authoritative hints parsed from {@code originalFilename}.
 * Always {@code INFERRED_DATA}; never identity, period of record, or totals.
 */
public record FilenameHints(
		String originalFilename,
		Optional<IncompleteDateRange> periodHint,
		Optional<IncompleteDate> singleDateHint
) {
	public FilenameHints {
		Objects.requireNonNull(originalFilename, "originalFilename");
		periodHint = periodHint == null ? Optional.empty() : periodHint;
		singleDateHint = singleDateHint == null ? Optional.empty() : singleDateHint;
	}

	public static FilenameHints empty(String originalFilename) {
		return new FilenameHints(originalFilename == null ? "" : originalFilename, Optional.empty(), Optional.empty());
	}

	public boolean isEmpty() {
		return periodHint.isEmpty() && singleDateHint.isEmpty();
	}
}
