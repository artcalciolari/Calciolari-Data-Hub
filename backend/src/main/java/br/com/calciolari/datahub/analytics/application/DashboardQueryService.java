package br.com.calciolari.datahub.analytics.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.calciolari.datahub.analytics.api.DashboardDtos.DailyPoint;
import br.com.calciolari.datahub.analytics.api.DashboardDtos.DashboardResponse;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;

@Service
public class DashboardQueryService {

	private final SaleRepository saleRepository;

	public DashboardQueryService(SaleRepository saleRepository) {
		this.saleRepository = saleRepository;
	}

	public DashboardResponse summarize(UUID productId, LocalDateTime from, LocalDateTime to) {
		LocalDateTime fromBound = from == null ? LocalDateTime.of(1, 1, 1, 0, 0) : from;
		LocalDateTime toBound = to == null ? LocalDateTime.of(9999, 12, 31, 23, 59, 59) : to;
		BigDecimal revenue = nullToZero(saleRepository.sumPublishedRevenue(productId, fromBound, toBound));
		BigDecimal quantity = nullToZero(saleRepository.sumPublishedQuantity(productId, fromBound, toBound));
		long salesCount = saleRepository.countPublishedSales(productId, fromBound, toBound);
		long itemCount = saleRepository.countPublishedItems(productId, fromBound, toBound);
		String averageTicket = salesCount > 0
				? revenue.divide(BigDecimal.valueOf(salesCount), 2, RoundingMode.HALF_UP).toPlainString()
				: null;

		Map<LocalDate, BigDecimal[]> byDay = new LinkedHashMap<>();
		for (Object[] row : saleRepository.publishedMovementFacts(productId, fromBound, toBound)) {
			LocalDateTime occurredAt = (LocalDateTime) row[0];
			BigDecimal qty = nullToZero((BigDecimal) row[1]);
			BigDecimal tot = nullToZero((BigDecimal) row[2]);
			LocalDate day = occurredAt.toLocalDate();
			byDay.compute(day, (d, acc) -> {
				if (acc == null) {
					return new BigDecimal[] {qty, tot};
				}
				acc[0] = acc[0].add(qty);
				acc[1] = acc[1].add(tot);
				return acc;
			});
		}
		List<DailyPoint> daily = new ArrayList<>();
		byDay.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.forEach(entry -> daily.add(new DailyPoint(
						entry.getKey().toString(),
						entry.getValue()[0].stripTrailingZeros().toPlainString(),
						entry.getValue()[1].stripTrailingZeros().toPlainString())));

		return new DashboardResponse(
				decimal(revenue),
				decimal(quantity),
				salesCount,
				itemCount,
				averageTicket,
				iso(saleRepository.minPublishedOccurredAt(productId, fromBound, toBound)),
				iso(saleRepository.maxPublishedOccurredAt(productId, fromBound, toBound)),
				daily);
	}

	private static BigDecimal nullToZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static String decimal(BigDecimal value) {
		return value == null ? null : value.stripTrailingZeros().toPlainString();
	}

	private static String iso(LocalDateTime value) {
		return value == null ? null : value.toString();
	}
}
