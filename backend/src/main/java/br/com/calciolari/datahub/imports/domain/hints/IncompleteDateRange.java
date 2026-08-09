package br.com.calciolari.datahub.imports.domain.hints;

import java.util.Objects;

/**
 * Inclusive period hint from a filename such as {@code 01_07-20_07}.
 * Provenance: {@code INFERRED_DATA} only.
 */
public record IncompleteDateRange(IncompleteDate start, IncompleteDate end) {

	public IncompleteDateRange {
		Objects.requireNonNull(start, "start");
		Objects.requireNonNull(end, "end");
	}
}
