package br.com.calciolari.datahub.imports.domain.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Totals declared by the source report and/or derived strictly from parsed
 * records. Calculated fields are provenance {@code CALCULATED_DATA}.
 */
public record ParsedImportTotals(
		BigDecimal sourceQuantityTotal,
		BigDecimal parsedQuantityTotal,
		BigDecimal sourceRevenueTotal,
		BigDecimal parsedRevenueTotal,
		LocalDateTime firstMovementAt,
		LocalDateTime lastMovementAt
) {
	public static ParsedImportTotals empty() {
		return new ParsedImportTotals(null, null, null, null, null, null);
	}
}
