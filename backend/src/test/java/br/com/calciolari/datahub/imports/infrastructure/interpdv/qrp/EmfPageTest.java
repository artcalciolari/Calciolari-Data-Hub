package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmfPageTest {

	@Test
	void validatesOffsets() {
		assertThrows(IllegalArgumentException.class, () -> new EmfPage(-1, 10));
		assertThrows(IllegalArgumentException.class, () -> new EmfPage(0, 0));
	}
}
