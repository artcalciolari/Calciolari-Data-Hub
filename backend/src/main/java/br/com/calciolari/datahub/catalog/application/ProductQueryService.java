package br.com.calciolari.datahub.catalog.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.catalog.api.ProductDtos.ProductDetail;
import br.com.calciolari.datahub.catalog.api.ProductDtos.ProductSummary;
import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductEntity;
import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductRepository;
import br.com.calciolari.datahub.shared.api.PageResponse;

@Service
public class ProductQueryService {

	private final ProductRepository productRepository;

	public ProductQueryService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	public PageResponse<ProductSummary> list(String query, int page, int size) {
		Page<ProductEntity> result = productRepository.searchPublished(
				blankToNull(query), PageRequest.of(page, size, org.springframework.data.domain.Sort.by("name")));
		List<ProductSummary> content = result.getContent().stream()
				.map(p -> new ProductSummary(p.getId(), p.getExternalSource(), p.getExternalId(), p.getName(), null))
				.toList();
		return PageResponse.of(content, page, size, result.getTotalElements());
	}

	public ProductDetail get(UUID id) {
		ProductEntity product = productRepository.findPublishedById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
		return new ProductDetail(
				product.getId(),
				product.getExternalSource(),
				product.getExternalId(),
				product.getName(),
				null,
				product.getFirstSeenParseAttemptId());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
