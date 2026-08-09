package br.com.calciolari.datahub.sales.api;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.calciolari.datahub.sales.api.SaleDtos.SaleDetail;
import br.com.calciolari.datahub.sales.api.SaleDtos.SaleSummary;
import br.com.calciolari.datahub.sales.application.SaleQueryService;
import br.com.calciolari.datahub.shared.api.PageParams;
import br.com.calciolari.datahub.shared.api.PageResponse;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

	private final SaleQueryService saleQueryService;

	public SaleController(SaleQueryService saleQueryService) {
		this.saleQueryService = saleQueryService;
	}

	@GetMapping
	public PageResponse<SaleSummary> list(
			@RequestParam(required = false) UUID productId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return saleQueryService.list(productId, from, to, PageParams.page(page), PageParams.size(size));
	}

	@GetMapping("/{id}")
	public SaleDetail get(@PathVariable UUID id) {
		return saleQueryService.get(id);
	}
}
