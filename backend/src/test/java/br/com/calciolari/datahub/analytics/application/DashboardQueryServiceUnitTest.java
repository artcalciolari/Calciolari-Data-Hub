package br.com.calciolari.datahub.analytics.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;

class DashboardQueryServiceUnitTest {

	@Test
	void nullBoundsZeroCountsAndDailyAggregation() {
		SaleRepository sales = mock(SaleRepository.class);
		DashboardQueryService service = new DashboardQueryService(sales);

		when(sales.sumPublishedRevenue(isNull(), any(), any())).thenReturn(null);
		when(sales.sumPublishedQuantity(isNull(), any(), any())).thenReturn(null);
		when(sales.countPublishedSales(isNull(), any(), any())).thenReturn(0L);
		when(sales.countPublishedItems(isNull(), any(), any())).thenReturn(0L);
		when(sales.publishedMovementFacts(isNull(), any(), any())).thenReturn(List.of());
		when(sales.topPublishedProducts(isNull(), any(), any(), any())).thenReturn(List.of());
		when(sales.minPublishedOccurredAt(isNull(), any(), any())).thenReturn(null);
		when(sales.maxPublishedOccurredAt(isNull(), any(), any())).thenReturn(null);

		var empty = service.summarize(null, null, null);
		assertEquals("0", empty.revenueTotal());
		assertNull(empty.averageTicket());
		assertNull(empty.firstMovementAt());
		assertEquals(0, empty.daily().size());

		LocalDateTime day = LocalDateTime.of(2024, 6, 1, 10, 0);
		UUID productId = UUID.randomUUID();
		when(sales.sumPublishedRevenue(eq(productId), any(), any())).thenReturn(new BigDecimal("20.00"));
		when(sales.sumPublishedQuantity(eq(productId), any(), any())).thenReturn(new BigDecimal("2"));
		when(sales.countPublishedSales(eq(productId), any(), any())).thenReturn(2L);
		when(sales.countPublishedItems(eq(productId), any(), any())).thenReturn(2L);
		when(sales.publishedMovementFacts(eq(productId), any(), any())).thenReturn(List.of(
				new Object[] {day, null, new BigDecimal("10")},
				new Object[] {day.plusHours(1), new BigDecimal("1"), null}));
		when(sales.topPublishedProducts(eq(productId), any(), any(), any())).thenReturn(List.<Object[]>of(
				new Object[] {productId, "N", "41", new BigDecimal("10.0"), new BigDecimal("10.0")}));
		when(sales.minPublishedOccurredAt(eq(productId), any(), any())).thenReturn(day);
		when(sales.maxPublishedOccurredAt(eq(productId), any(), any())).thenReturn(day.plusHours(1));

		var filled = service.summarize(productId, day, day.plusDays(1));
		assertEquals("10.00", filled.averageTicket());
		assertEquals(1, filled.daily().size());
		assertEquals(1, filled.topProducts().size());
		assertEquals(day.toString(), filled.firstMovementAt());
	}

	@Test
	void decimalHelperReturnsNullForNullInput() throws Exception {
		var decimal = DashboardQueryService.class.getDeclaredMethod("decimal", BigDecimal.class);
		decimal.setAccessible(true);
		assertNull(decimal.invoke(null, new Object[] {null}));
	}
}
