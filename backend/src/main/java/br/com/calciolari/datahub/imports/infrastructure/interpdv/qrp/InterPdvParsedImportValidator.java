package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.IssueStage;
import br.com.calciolari.datahub.imports.domain.parser.ParseIssue;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.SourceLocator;

/**
 * Deterministic validations over a {@link ParsedImport}. Source item totals are
 * preserved; calculated values exist only for comparison.
 */
public final class InterPdvParsedImportValidator {

	public static final String RULE_VERSION = "interpdv-validation-v1";

	private final BigDecimal moneyTolerance;
	private final BigDecimal quantityTolerance;

	public InterPdvParsedImportValidator() {
		this(new BigDecimal("0.01"), new BigDecimal("0.001"));
	}

	public InterPdvParsedImportValidator(BigDecimal moneyTolerance, BigDecimal quantityTolerance) {
		this.moneyTolerance = Objects.requireNonNull(moneyTolerance, "moneyTolerance");
		this.quantityTolerance = Objects.requireNonNull(quantityTolerance, "quantityTolerance");
	}

	public List<ParseIssue> validate(ParsedImport parsed) {
		Objects.requireNonNull(parsed, "parsed");
		List<ParseIssue> issues = new ArrayList<>();

		BigDecimal sourceQty = parsed.totals().sourceQuantityTotal();
		BigDecimal parsedQty = parsed.totals().parsedQuantityTotal();
		if (sourceQty != null && parsedQty != null) {
			BigDecimal diff = parsedQty.subtract(sourceQty).abs();
			if (diff.compareTo(quantityTolerance) <= 0) {
				issues.add(new ParseIssue(
						"SOURCE_QUANTITY_MATCH",
						IssueSeverity.INFO,
						IssueStage.VALIDATION,
						SourceLocator.empty(),
						"sourceValue=" + sourceQty.toPlainString()
								+ " calculatedValue=" + parsedQty.toPlainString()
								+ " difference=" + diff.toPlainString()
								+ " tolerance=" + quantityTolerance.toPlainString()
								+ " ruleVersion=" + RULE_VERSION));
			}
			else {
				issues.add(new ParseIssue(
						"SOURCE_QUANTITY_MISMATCH",
						IssueSeverity.ERROR,
						IssueStage.VALIDATION,
						SourceLocator.empty(),
						"sourceValue=" + sourceQty.toPlainString()
								+ " calculatedValue=" + parsedQty.toPlainString()
								+ " difference=" + diff.toPlainString()
								+ " tolerance=" + quantityTolerance.toPlainString()
								+ " ruleVersion=" + RULE_VERSION));
			}
		}

		for (ParsedMovement movement : parsed.movements()) {
			validateLineTotal(movement, issues);
			validateStockContinuity(movement, issues);
		}
		return List.copyOf(issues);
	}

	private void validateLineTotal(ParsedMovement movement, List<ParseIssue> issues) {
		if (movement.quantity() == null || movement.unitPrice() == null || movement.total() == null) {
			return;
		}
		BigDecimal discount = movement.discountPercentage() == null ? BigDecimal.ZERO : movement.discountPercentage();
		BigDecimal factor = BigDecimal.ONE.subtract(discount.divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP));
		BigDecimal calculated = movement.quantity().multiply(movement.unitPrice()).multiply(factor)
				.setScale(2, RoundingMode.HALF_UP);
		BigDecimal diff = calculated.subtract(movement.total()).abs();
		String code = diff.compareTo(moneyTolerance) <= 0 ? "LINE_TOTAL_MATCH" : "LINE_TOTAL_MISMATCH";
		IssueSeverity severity = diff.compareTo(moneyTolerance) <= 0 ? IssueSeverity.INFO : IssueSeverity.WARNING;
		issues.add(new ParseIssue(
				code,
				severity,
				IssueStage.VALIDATION,
				movement.sourceLocator(),
				"sourceValue=" + movement.total().toPlainString()
						+ " calculatedValue=" + calculated.toPlainString()
						+ " difference=" + diff.toPlainString()
						+ " tolerance=" + moneyTolerance.toPlainString()
						+ " ruleVersion=" + RULE_VERSION));
	}

	private void validateStockContinuity(ParsedMovement movement, List<ParseIssue> issues) {
		if (movement.previousStock() == null || movement.resultingStock() == null || movement.quantity() == null) {
			return;
		}
		// Negative stock is allowed; only check arithmetic continuity for OUT movements.
		if (movement.direction() != br.com.calciolari.datahub.imports.domain.parser.MovementDirection.OUT) {
			return;
		}
		BigDecimal expected = movement.previousStock().subtract(movement.quantity());
		BigDecimal diff = expected.subtract(movement.resultingStock()).abs();
		if (diff.compareTo(quantityTolerance) > 0) {
			issues.add(new ParseIssue(
					"STOCK_CONTINUITY_MISMATCH",
					IssueSeverity.WARNING,
					IssueStage.VALIDATION,
					movement.sourceLocator(),
					"previous=" + movement.previousStock().toPlainString()
							+ " quantity=" + movement.quantity().toPlainString()
							+ " resulting=" + movement.resultingStock().toPlainString()
							+ " expected=" + expected.toPlainString()));
		}
	}
}
