package br.com.calciolari.datahub.imports.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ImportLimitsPropertiesTest {

	@Test
	void gettersAndSetters() {
		ImportLimitsProperties props = new ImportLimitsProperties();
		assertEquals(20, props.getMaxFiles());
		assertEquals(32L * 1024 * 1024, props.getMaxFileBytes());
		props.setMaxFiles(2);
		props.setMaxFileBytes(1024);
		assertEquals(2, props.getMaxFiles());
		assertEquals(1024, props.getMaxFileBytes());
	}
}
