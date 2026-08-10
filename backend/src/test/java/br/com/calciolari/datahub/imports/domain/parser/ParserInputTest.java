package br.com.calciolari.datahub.imports.domain.parser;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

class ParserInputTest {

	@Test
	void validatesContentLength() {
		assertThrows(IllegalArgumentException.class,
				() -> new ParserInput(new ByteArrayInputStream(new byte[0]), -1, "f.qrp", "QRP"));
		assertTrue(new ParserInput(new ByteArrayInputStream(new byte[0]), 0, "  ", "QRP")
				.originalFilenameOptional()
				.isEmpty());
		assertTrue(new ParserInput(new ByteArrayInputStream(new byte[0]), 0, "name.qrp", "QRP")
				.originalFilenameOptional()
				.isPresent());
	}
}
