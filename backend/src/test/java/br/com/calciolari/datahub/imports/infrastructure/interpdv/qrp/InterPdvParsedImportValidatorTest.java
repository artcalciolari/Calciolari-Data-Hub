package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.IssueStage;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;
import br.com.calciolari.datahub.imports.domain.parser.ParseIssue;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportStats;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportTotals;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.SourceLocator;

class InterPdvParsedImportValidatorTest {

	private final InterPdvParsedImportValidator validator = new InterPdvParsedImportValidator();

	@Test
	void quantityMatchAndMismatch() {
		ParsedImport match = sampleImport(
				new ParsedImportTotals(new BigDecimal("10"), new BigDecimal("10.0005"), null, null, null, null),
				List.of());
		assertTrue(validator.validate(match).stream().anyMatch(i -> "SOURCE_QUANTITY_MATCH".equals(i.code())));

		ParsedImport mismatch = sampleImport(
				new ParsedImportTotals(new BigDecimal("10"), new BigDecimal("5"), null, null, null, null),
				List.of());
		assertTrue(validator.validate(mismatch).stream()
				.anyMatch(i -> "SOURCE_QUANTITY_MISMATCH".equals(i.code()) && i.severity() == IssueSeverity.ERROR));
	}

	@Test
	void lineTotalAndStockContinuity() {
		ParsedMovement okLine = movement(MovementDirection.OUT, "1", new BigDecimal("2"), new BigDecimal("3"),
				new BigDecimal("6"), null, new BigDecimal("10"), new BigDecimal("8"));
		ParsedMovement badStock = movement(MovementDirection.OUT, "2", new BigDecimal("1"), new BigDecimal("1"),
				new BigDecimal("1"), null, new BigDecimal("5"), new BigDecimal("1"));
		ParsedMovement inOnly = movement(MovementDirection.IN, "3", new BigDecimal("1"), null,
				null, null, new BigDecimal("1"), new BigDecimal("2"));

		var issues = validator.validate(sampleImport(ParsedImportTotals.empty(), List.of(okLine, badStock, inOnly)));
		assertTrue(issues.stream().anyMatch(i -> i.code().startsWith("LINE_TOTAL")));
		assertTrue(issues.stream().anyMatch(i -> "STOCK_CONTINUITY_MISMATCH".equals(i.code())));
	}

	@Test
	void skipsIncompleteMovements() {
		ParsedMovement sparse = new ParsedMovement(
				0, MovementDirection.OUT, null, null, null, null,
				null, null, null, null, null, null, null, SourceLocator.empty());
		assertTrue(validator.validate(sampleImport(ParsedImportTotals.empty(), List.of(sparse))).isEmpty());
	}

	private static ParsedImport sampleImport(ParsedImportTotals totals, List<ParsedMovement> movements) {
		return new ParsedImport(
				"INTERPDV", "p", "v", "1", "n", movements, totals,
				ParsedImportStats.empty(), List.of());
	}

	private static ParsedMovement movement(
			MovementDirection direction,
			String saleId,
			BigDecimal qty,
			BigDecimal price,
			BigDecimal total,
			BigDecimal discount,
			BigDecimal previous,
			BigDecimal resulting) {
		return new ParsedMovement(
				0, direction, "1", "n", saleId, null,
				qty, price, discount, total, previous, resulting, null, SourceLocator.empty());
	}
}
