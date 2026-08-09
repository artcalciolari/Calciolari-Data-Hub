package br.com.calciolari.datahub.catalog.api;

import java.util.UUID;

public final class ProductDtos {

	private ProductDtos() {
	}

	public record ProductSummary(
			UUID id,
			String externalSource,
			String externalId,
			String name,
			String unit
	) {
	}

	public record ProductDetail(
			UUID id,
			String externalSource,
			String externalId,
			String name,
			String unit,
			UUID firstSeenParseAttemptId
	) {
	}
}
