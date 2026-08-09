package br.com.calciolari.datahub.analytics.api;

import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

	private DashboardDtos() {
	}

	public record DashboardResponse(
			String revenueTotal,
			String quantityTotal,
			long salesCount,
			long itemCount,
			String averageTicket,
			String firstMovementAt,
			String lastMovementAt,
			List<DailyPoint> daily,
			List<TopProduct> topProducts
	) {
	}

	public record DailyPoint(
			String date,
			String quantity,
			String revenue
	) {
	}

	public record TopProduct(
			UUID productId,
			String name,
			String externalId,
			String quantity,
			String revenue
	) {
	}
}
