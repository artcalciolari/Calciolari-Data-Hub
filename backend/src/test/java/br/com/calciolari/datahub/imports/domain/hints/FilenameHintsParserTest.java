package br.com.calciolari.datahub.imports.domain.hints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FilenameHintsParserTest {

	private final FilenameHintsParser parser = new FilenameHintsParser();

	@Test
	void parsesIncompletePeriodWithoutInventingYear() {
		FilenameHints hints = parser.parse("AUDITORIA 41, 01_07-20_07.QRP");
		assertEquals("AUDITORIA 41, 01_07-20_07.QRP", hints.originalFilename());
		assertTrue(hints.periodHint().isPresent());
		IncompleteDateRange range = hints.periodHint().orElseThrow();
		assertEquals(1, range.start().day());
		assertEquals(7, range.start().month());
		assertTrue(range.start().year().isEmpty());
		assertEquals(20, range.end().day());
		assertEquals(7, range.end().month());
		assertTrue(range.end().year().isEmpty());
		assertTrue(hints.singleDateHint().isEmpty());
	}

	@Test
	void returnsEmptyForAmbiguousNames() {
		assertTrue(parser.parse("AUDITORIA.QRP").isEmpty());
		assertTrue(parser.parse("AUDITORIA JULHO FINAL.QRP").isEmpty());
		assertTrue(parser.parse("random-file.qrp").isEmpty());
	}

	@Test
	void neverThrowsOnNullOrGarbage() {
		assertEquals("", parser.parse(null).originalFilename());
		assertTrue(parser.parse("???@@@").isEmpty());
	}

	@Test
	void parsesSingleUnderscoreDateAsIncompleteHint() {
		FilenameHints hints = parser.parse("relatorio_20_07.QRP");
		assertTrue(hints.singleDateHint().isPresent());
		assertEquals(20, hints.singleDateHint().orElseThrow().day());
		assertEquals(7, hints.singleDateHint().orElseThrow().month());
		assertTrue(hints.singleDateHint().orElseThrow().year().isEmpty());
	}

	@Test
	void parsesSlashPeriodRange() {
		FilenameHints hints = parser.parse("AUDITORIA 01/07-20/07.QRP");
		assertTrue(hints.periodHint().isPresent());
	}

	@Test
	void stripsWindowsDirectoryPrefix() {
		FilenameHints hints = parser.parse("folder\\relatorio_20_07.QRP");
		assertTrue(hints.singleDateHint().isPresent());
	}

	@Test
	void invalidDayOrMonthViaReflection() throws Exception {
		var toDate = FilenameHintsParser.class.getDeclaredMethod("toDate", String.class, String.class);
		toDate.setAccessible(true);
		assertTrue(((java.util.Optional<?>) toDate.invoke(null, "xx", "01")).isEmpty());
		assertTrue(((java.util.Optional<?>) toDate.invoke(null, "01", "yy")).isEmpty());
		assertTrue(parser.parse("x_00_07.QRP").isEmpty());
		assertTrue(parser.parse("x_01_00.QRP").isEmpty());
	}

	@Test
	void toRangeEmptyWhenEitherEndInvalid() throws Exception {
		var toRange = FilenameHintsParser.class.getDeclaredMethod(
				"toRange", String.class, String.class, String.class, String.class);
		toRange.setAccessible(true);
		assertTrue(((java.util.Optional<?>) toRange.invoke(null, "32", "01", "01", "01")).isEmpty());
		assertTrue(((java.util.Optional<?>) toRange.invoke(null, "01", "01", "32", "01")).isEmpty());
	}

	@Test
	void matchSingleFallsBackToSlashPattern() {
		FilenameHints hints = parser.parse("nota_15/08.QRP");
		assertTrue(hints.singleDateHint().isPresent());
		assertEquals(15, hints.singleDateHint().orElseThrow().day());
		assertEquals(8, hints.singleDateHint().orElseThrow().month());
	}
}
