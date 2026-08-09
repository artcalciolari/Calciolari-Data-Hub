package br.com.calciolari.datahub.shared.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ProvenanceKindTest {

	@Test
	void valuesAreStable() {
		assertEquals(3, ProvenanceKind.values().length);
		assertEquals(ProvenanceKind.SOURCE_DATA, ProvenanceKind.valueOf("SOURCE_DATA"));
		assertEquals(ProvenanceKind.CALCULATED_DATA, ProvenanceKind.valueOf("CALCULATED_DATA"));
		assertEquals(ProvenanceKind.INFERRED_DATA, ProvenanceKind.valueOf("INFERRED_DATA"));
	}
}
