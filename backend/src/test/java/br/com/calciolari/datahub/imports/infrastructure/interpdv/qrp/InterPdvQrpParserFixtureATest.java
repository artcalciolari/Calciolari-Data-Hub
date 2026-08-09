package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.domain.hints.FilenameHintsParser;
import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.ParserInput;
import br.com.calciolari.datahub.imports.support.FixturePackage;

/**
 * Gold regression for Fixture A (NHOQUE BATATA) — IMPLEMENTATION_PLAN §13.2.
 */
class InterPdvQrpParserFixtureATest {

	@BeforeAll
	static void forceLocaleTimezone() {
		Locale.setDefault(Locale.GERMANY);
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
	}

	@Test
	void fixtureAMatchesAcceptanceCriteria() {
		assumeTrue(FixturePackage.isPresent("fixture-a"), "Fixture A binary required");

		byte[] bytes = FixturePackage.requireBytes("fixture-a");
		ParsedImport parsed = new InterPdvQrpParser().parse(new ParserInput(
				new ByteArrayInputStream(bytes),
				bytes.length,
				"AUDITORIA.QRP",
				"QRP"));

		assertEquals("35", parsed.externalProductId());
		assertEquals("NHOQUE BATATA", parsed.productName());
		assertEquals(1, parsed.stats().pages());
		assertEquals(15, parsed.stats().lines());
		assertEquals(new BigDecimal("10.842"), parsed.totals().sourceQuantityTotal());
		assertEquals(0, parsed.totals().sourceQuantityTotal().compareTo(parsed.totals().parsedQuantityTotal()));

		ParsedMovement sample = parsed.movements().stream()
				.filter(m -> "134808".equals(m.externalSaleId()))
				.filter(m -> m.total() != null && m.total().compareTo(new BigDecimal("32.59")) == 0)
				.findFirst()
				.orElseThrow();

		assertEquals(MovementDirection.OUT, sample.direction());
		assertEquals(LocalDateTime.of(2026, 8, 7, 12, 22, 13), sample.occurredAt());
		// Source text is "0,51"; plan writes 0.510 — compare numerically.
		assertEquals(0, new BigDecimal("0.510").compareTo(sample.quantity()));
		assertEquals(0, new BigDecimal("63.90").compareTo(sample.unitPrice()));
		assertEquals(0, new BigDecimal("32.59").compareTo(sample.total()));
		assertEquals(0, BigDecimal.ZERO.compareTo(sample.discountPercentage()));

		assertTrue(parsed.issues().stream().anyMatch(i ->
				"SOURCE_QUANTITY_MATCH".equals(i.code()) && i.severity() == IssueSeverity.INFO));
		assertTrue(parsed.issues().stream().anyMatch(i ->
				"LINE_TOTAL_MATCH".equals(i.code())
						&& i.sourceLocator() != null
						&& Integer.valueOf(sample.sourceRecordIndex()).equals(i.sourceLocator().recordIndex())));
		assertFalse(parsed.hasFatalOrError());

		assertTrue(new FilenameHintsParser().parse("AUDITORIA.QRP").isEmpty());
	}
}
