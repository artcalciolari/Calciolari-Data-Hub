package br.com.calciolari.datahub.catalog.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.catalog.api.ProductDtos.ProductDetail;
import br.com.calciolari.datahub.catalog.api.ProductDtos.ProductSummary;
import br.com.calciolari.datahub.catalog.application.ProductQueryService;
import br.com.calciolari.datahub.shared.api.PageParams;
import br.com.calciolari.datahub.shared.api.PageResponse;

class ProductControllerUnitTest {

	ProductQueryService queryService;
	ProductController controller;

	@BeforeEach
	void setUp() {
		queryService = mock(ProductQueryService.class);
		controller = new ProductController(queryService);
	}

	@Test
	void listAppliesPagingDefaultsWhenPageAndSizeAreAbsent() {
		PageResponse<ProductSummary> expected = PageResponse.of(List.of(), 0, PageParams.DEFAULT_SIZE, 0);
		when(queryService.list(isNull(), eq(0), eq(PageParams.DEFAULT_SIZE))).thenReturn(expected);

		assertSame(expected, controller.list(null, null, null));
	}

	@Test
	void listForwardsSearchTermAndPaging() {
		PageResponse<ProductSummary> expected = PageResponse.of(
				List.of(new ProductSummary(UUID.randomUUID(), "INTERPDV", "P-1", "Cafe", "UN")), 1, 5, 6);
		when(queryService.list("cafe", 1, 5)).thenReturn(expected);

		assertSame(expected, controller.list("cafe", 1, 5));
	}

	@Test
	void listRejectsInvalidPaging() {
		assertEquals("page must be >= 0", reason(() -> controller.list(null, -1, null)));
		assertEquals("size must be between 1 and " + PageParams.MAX_SIZE,
				reason(() -> controller.list(null, null, PageParams.MAX_SIZE + 1)));
	}

	@Test
	void getDelegatesToQueryService() {
		UUID id = UUID.randomUUID();
		ProductDetail expected = new ProductDetail(id, "INTERPDV", "P-1", "Cafe", "UN", UUID.randomUUID());
		when(queryService.get(id)).thenReturn(expected);

		assertSame(expected, controller.get(id));
	}

	private static String reason(Runnable call) {
		return assertThrows(ResponseStatusException.class, call::run).getReason();
	}
}
