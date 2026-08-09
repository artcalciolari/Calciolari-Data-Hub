package br.com.calciolari.datahub.catalog.api;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.calciolari.datahub.catalog.api.ProductDtos.ProductDetail;
import br.com.calciolari.datahub.catalog.api.ProductDtos.ProductSummary;
import br.com.calciolari.datahub.catalog.application.ProductQueryService;
import br.com.calciolari.datahub.shared.api.PageParams;
import br.com.calciolari.datahub.shared.api.PageResponse;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductQueryService productQueryService;

	public ProductController(ProductQueryService productQueryService) {
		this.productQueryService = productQueryService;
	}

	@GetMapping
	public PageResponse<ProductSummary> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return productQueryService.list(q, PageParams.page(page), PageParams.size(size));
	}

	@GetMapping("/{id}")
	public ProductDetail get(@PathVariable UUID id) {
		return productQueryService.get(id);
	}
}
