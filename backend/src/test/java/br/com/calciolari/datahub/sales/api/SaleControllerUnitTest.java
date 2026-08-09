package br.com.calciolari.datahub.sales.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.sales.api.SaleDtos.SaleDetail;
import br.com.calciolari.datahub.sales.api.SaleDtos.SaleSummary;
import br.com.calciolari.datahub.sales.application.SaleQueryService;
import br.com.calciolari.datahub.shared.api.PageParams;
import br.com.calciolari.datahub.shared.api.PageResponse;

class SaleControllerUnitTest {

	SaleQueryService queryService;
	SaleController controller;

	@BeforeEach
	void setUp() {
		queryService = mock(SaleQueryService.class);
		controller = new SaleController(queryService);
	}

	@Test
	void listAppliesPagingDefaultsWhenPageAndSizeAreAbsent() {
		PageResponse<SaleSummary> expected = PageResponse.of(List.of(), 0, PageParams.DEFAULT_SIZE, 0);
		when(queryService.list(isNull(), isNull(), isNull(), eq(0), eq(PageParams.DEFAULT_SIZE)))
				.thenReturn(expected);

		assertSame(expected, controller.list(null, null, null, null, null));
	}

	@Test
	void listForwardsExplicitFilters() {
		UUID productId = UUID.randomUUID();
		LocalDateTime from = LocalDateTime.of(2024, 7, 1, 0, 0);
		LocalDateTime to = LocalDateTime.of(2024, 7, 31, 23, 59);
		PageResponse<SaleSummary> expected = PageResponse.of(
				List.of(new SaleSummary(UUID.randomUUID(), "INTERPDV", "S-1", "2024-07-15T10:00", "10.00")), 2, 5, 11);
		when(queryService.list(productId, from, to, 2, 5)).thenReturn(expected);

		assertSame(expected, controller.list(productId, from, to, 2, 5));
	}

	@Test
	void listRejectsInvalidPaging() {
		assertEquals("page must be >= 0", reason(() -> controller.list(null, null, null, -1, null)));
		assertEquals("size must be between 1 and " + PageParams.MAX_SIZE,
				reason(() -> controller.list(null, null, null, null, 0)));
	}

	@Test
	void getDelegatesToQueryService() {
		UUID id = UUID.randomUUID();
		SaleDetail expected = new SaleDetail(id, "INTERPDV", "S-1", "2024-07-15T10:00", List.of());
		when(queryService.get(id)).thenReturn(expected);

		assertSame(expected, controller.get(id));
	}

	private static String reason(Runnable call) {
		return assertThrows(ResponseStatusException.class, call::run).getReason();
	}
}
