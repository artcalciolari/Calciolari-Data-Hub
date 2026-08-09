package br.com.calciolari.datahub.shared.provenance;

/**
 * Structural provenance for the MVP. Inferred data must never silently fill or
 * overwrite canonical source fields.
 */
public enum ProvenanceKind {
	SOURCE_DATA,
	CALCULATED_DATA,
	INFERRED_DATA
}
