package br.com.calciolari.datahub.catalog.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.catalog.api.ProductDtos.ProductDetail;
import br.com.calciolari.datahub.catalog.application.ProductQueryService;

class ProductControllerUnitTest {

	@Test
	void getDelegates() {
		ProductQueryService query = mock(ProductQueryService.class);
		ProductController controller = new ProductController(query);
		UUID id = UUID.randomUUID();
		ProductDetail detail = mock(ProductDetail.class);
		when(query.get(id)).thenReturn(detail);
		assertSame(detail, controller.get(id));
	}
}
