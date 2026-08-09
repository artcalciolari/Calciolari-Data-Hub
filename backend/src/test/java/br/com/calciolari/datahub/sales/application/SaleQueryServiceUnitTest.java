package br.com.calciolari.datahub.sales.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductEntity;
import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductRepository;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleEntity;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemEntity;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemRepository;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;

class SaleQueryServiceUnitTest {

	@Test
	void boundsAndNullDecimals() {
		assertEquals(LocalDateTime.of(1, 1, 1, 0, 0), SaleQueryService.boundFrom(null));
		LocalDateTime now = LocalDateTime.of(2024, 1, 2, 3, 4);
		assertEquals(now, SaleQueryService.boundFrom(now));
		assertEquals(LocalDateTime.of(9999, 12, 31, 23, 59, 59), SaleQueryService.boundTo(null));
		assertEquals(now, SaleQueryService.boundTo(now));

		SaleRepository sales = mock(SaleRepository.class);
		SaleItemRepository items = mock(SaleItemRepository.class);
		ProductRepository products = mock(ProductRepository.class);
		SaleQueryService service = new SaleQueryService(sales, items, products);

		UUID saleId = UUID.randomUUID();
		SaleEntity sale = new SaleEntity(saleId, "INTERPDV", "S1", null, UUID.randomUUID());
		when(sales.searchPublished(isNull(), any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(sale), PageRequest.of(0, 10), 1));
		when(items.sumPublishedTotalForSale(saleId)).thenReturn(null);

		var page = service.list(null, null, null, 0, 10);
		assertEquals(1, page.content().size());
		assertNull(page.content().getFirst().total());
		assertNull(page.content().getFirst().occurredAt());
	}

	@Test
	void getMapsItemsWithNullDecimals() {
		SaleRepository sales = mock(SaleRepository.class);
		SaleItemRepository items = mock(SaleItemRepository.class);
		ProductRepository products = mock(ProductRepository.class);
		SaleQueryService service = new SaleQueryService(sales, items, products);

		UUID saleId = UUID.randomUUID();
		UUID productId = UUID.randomUUID();
		SaleEntity sale = new SaleEntity(saleId, "INTERPDV", "S1", LocalDateTime.of(2024, 1, 1, 0, 0), UUID.randomUUID());
		when(sales.findPublishedById(saleId)).thenReturn(Optional.of(sale));
		SaleItemEntity item = new SaleItemEntity(
				UUID.randomUUID(), saleId, productId, UUID.randomUUID(), 0,
				BigDecimal.ONE, BigDecimal.TEN, null, BigDecimal.TEN, null, null);
		when(items.findPublishedBySaleId(saleId)).thenReturn(List.of(item));
		when(products.findById(productId)).thenReturn(Optional.of(
				new ProductEntity(productId, "INTERPDV", "41", "N", UUID.randomUUID())));

		var detail = service.get(saleId);
		assertEquals(1, detail.items().size());
		assertNull(detail.items().getFirst().discountPercentage());

		when(sales.findPublishedById(saleId)).thenReturn(Optional.empty());
		assertThrows(ResponseStatusException.class, () -> service.get(saleId));
	}
}
