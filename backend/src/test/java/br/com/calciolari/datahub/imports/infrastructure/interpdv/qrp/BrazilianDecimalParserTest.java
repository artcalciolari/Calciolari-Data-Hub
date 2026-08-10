package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class BrazilianDecimalParserTest {

	@Test
	void parsesBrazilianGroupedMoney() {
		assertEquals(new BigDecimal("56.90"), BrazilianDecimalParser.parse("R$ 56,90"));
		assertEquals(new BigDecimal("3013.07"), BrazilianDecimalParser.parse("3.013,07"));
	}

	@Test
	void parsesPlainQuantity() {
		assertEquals(new BigDecimal("52.986"), BrazilianDecimalParser.parse("52,986"));
		assertEquals(new BigDecimal("0.416"), BrazilianDecimalParser.parse("0,416"));
	}

	@Test
	void returnsNullForBlank() {
		assertNull(BrazilianDecimalParser.parse(null));
		assertNull(BrazilianDecimalParser.parse(""));
		assertNull(BrazilianDecimalParser.parse("   "));
	}

	@Test
	void parsesDotOnlyDecimalsAndRejectsGarbage() {
		assertEquals(new BigDecimal("12.34"), BrazilianDecimalParser.parse("12.34"));
		assertEquals(new BigDecimal("5"), BrazilianDecimalParser.parse("5"));
		assertNull(BrazilianDecimalParser.parse("-"));
		assertNull(BrazilianDecimalParser.parse("not-a-number"));
	}
}
