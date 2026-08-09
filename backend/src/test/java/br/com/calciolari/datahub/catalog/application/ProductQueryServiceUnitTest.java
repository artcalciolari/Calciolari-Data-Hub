package br.com.calciolari.datahub.catalog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductEntity;
import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductRepository;

class ProductQueryServiceUnitTest {

	@Test
	void blankQueryBecomesNullAndNotFound() {
		ProductRepository repo = mock(ProductRepository.class);
		ProductQueryService service = new ProductQueryService(repo);
		UUID id = UUID.randomUUID();
		ProductEntity product = new ProductEntity(id, "INTERPDV", "41", "NAME", UUID.randomUUID());

		when(repo.searchPublished(isNull(), any()))
				.thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 10), 1));
		assertEquals(1, service.list("  ", 0, 10).content().size());
		verify(repo).searchPublished(isNull(), any());

		when(repo.searchPublished(org.mockito.ArgumentMatchers.eq("abc"), any()))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));
		assertEquals(0, service.list(" abc ", 0, 10).totalElements());

		when(repo.findPublishedById(id)).thenReturn(Optional.of(product));
		assertEquals("NAME", service.get(id).name());

		when(repo.findPublishedById(id)).thenReturn(Optional.empty());
		assertThrows(ResponseStatusException.class, () -> service.get(id));
	}
}
