package br.com.calciolari.datahub.analytics.api;

import java.util.List;

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
			List<DailyPoint> daily
	) {
	}

	public record DailyPoint(
			String date,
			String quantity,
			String revenue
	) {
	}
}
