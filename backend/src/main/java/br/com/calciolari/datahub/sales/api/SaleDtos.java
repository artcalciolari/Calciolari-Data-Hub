package br.com.calciolari.datahub.sales.api;

import java.util.List;
import java.util.UUID;

public final class SaleDtos {

	private SaleDtos() {
	}

	public record SaleSummary(
			UUID id,
			String externalSource,
			String externalSaleId,
			String occurredAt,
			String total
	) {
	}

	public record SaleDetail(
			UUID id,
			String externalSource,
			String externalSaleId,
			String occurredAt,
			List<SaleItemDto> items
	) {
	}

	public record SaleItemDto(
			UUID id,
			UUID productId,
			String productName,
			String productExternalId,
			int sourceRecordIndex,
			String quantity,
			String unitPrice,
			String discountPercentage,
			String total
	) {
	}
}
