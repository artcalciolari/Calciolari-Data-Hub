package br.com.calciolari.datahub.imports.domain.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Normalized source movement. Money and quantities are {@link BigDecimal} only.
 */
public record ParsedMovement(
		int sourceRecordIndex,
		MovementDirection direction,
		String externalProductId,
		String productName,
		String externalSaleId,
		LocalDateTime occurredAt,
		BigDecimal quantity,
		BigDecimal unitPrice,
		BigDecimal discountPercentage,
		BigDecimal total,
		BigDecimal previousStock,
		BigDecimal resultingStock,
		String manufacturer,
		SourceLocator sourceLocator
) {
	public ParsedMovement {
		Objects.requireNonNull(direction, "direction");
		if (sourceLocator == null) {
			sourceLocator = SourceLocator.empty();
		}
	}
}
