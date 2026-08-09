package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.domain.hints.FilenameHints;
import br.com.calciolari.datahub.imports.domain.hints.FilenameHintsParser;
import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.ParserInput;
import br.com.calciolari.datahub.imports.support.FixturePackage;

/**
 * Gold regression for Fixture B (MOLHO POMODORO) — IMPLEMENTATION_PLAN §13.2.
 */
class InterPdvQrpParserFixtureBTest {

	@BeforeAll
	static void forceLocaleTimezone() {
		Locale.setDefault(Locale.GERMANY);
		TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
	}

	@Test
	void fixtureBMatchesAcceptanceCriteria() {
		assumeTrue(FixturePackage.isPresent("fixture-b"), "Fixture B binary required");

		byte[] bytes = FixturePackage.requireBytes("fixture-b");
		InterPdvQrpParser parser = new InterPdvQrpParser();
		ParsedImport parsed = parser.parse(new ParserInput(
				new ByteArrayInputStream(bytes),
				bytes.length,
				"AUDITORIA 41, 01_07-20_07.QRP",
				"QRP"));

		assertEquals("41", parsed.externalProductId());
		assertEquals("MOLHO POMODORO", parsed.productName());
		assertEquals(4, parsed.stats().pages());
		assertEquals(134, parsed.stats().lines());
		assertEquals(93, parsed.stats().uniqueSales());
		assertEquals(0, parsed.stats().entries());

		assertEquals(new BigDecimal("52.986"), parsed.totals().sourceQuantityTotal());
		assertEquals(new BigDecimal("52.986"), parsed.totals().parsedQuantityTotal());
		assertEquals(0, parsed.totals().sourceQuantityTotal().compareTo(parsed.totals().parsedQuantityTotal()));
		assertEquals(new BigDecimal("3013.07"), parsed.totals().parsedRevenueTotal());
		assertEquals(LocalDateTime.of(2026, 7, 19, 13, 7, 3), parsed.totals().lastMovementAt());

		ParsedMovement sample = parsed.movements().stream()
				.filter(m -> "134409".equals(m.externalSaleId()))
				.findFirst()
				.orElseThrow();
		assertEquals(MovementDirection.OUT, sample.direction());
		assertEquals(new BigDecimal("0.416"), sample.quantity());
		assertEquals(new BigDecimal("56.90"), sample.unitPrice());
		assertEquals(new BigDecimal("8"), sample.discountPercentage());
		assertEquals(new BigDecimal("21.78"), sample.total());

		assertTrue(parsed.issues().stream().anyMatch(i ->
				"SOURCE_QUANTITY_MATCH".equals(i.code()) && i.severity() == IssueSeverity.INFO));
		assertFalse(parsed.hasFatalOrError());

		FilenameHints hints = new FilenameHintsParser().parse("AUDITORIA 41, 01_07-20_07.QRP");
		assertTrue(hints.periodHint().isPresent());
		assertEquals(20, hints.periodHint().orElseThrow().end().day());
		assertEquals(7, hints.periodHint().orElseThrow().end().month());
		// Filename hint 20/07 must not override source last movement 19/07.
		assertNotNull(parsed.totals().lastMovementAt());
		assertEquals(19, parsed.totals().lastMovementAt().getDayOfMonth());
	}

	@Test
	void randomBytesDoNotPublishRows() {
		byte[] garbage = new byte[256];
		for (int i = 0; i < garbage.length; i++) {
			garbage[i] = (byte) (i * 37);
		}
		ParsedImport parsed = new InterPdvQrpParser().parse(new ParserInput(
				new ByteArrayInputStream(garbage), garbage.length, "noise.qrp", "QRP"));
		assertTrue(parsed.movements().isEmpty());
		assertTrue(parsed.hasFatalOrError());
		assertTrue(parsed.issues().stream().anyMatch(i -> "NO_EMF_PAGES".equals(i.code())));
	}
}
