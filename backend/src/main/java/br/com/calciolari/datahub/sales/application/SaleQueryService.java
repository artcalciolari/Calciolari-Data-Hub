package br.com.calciolari.datahub.sales.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductEntity;
import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductRepository;
import br.com.calciolari.datahub.sales.api.SaleDtos.SaleDetail;
import br.com.calciolari.datahub.sales.api.SaleDtos.SaleItemDto;
import br.com.calciolari.datahub.sales.api.SaleDtos.SaleSummary;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleEntity;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemEntity;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemRepository;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;
import br.com.calciolari.datahub.shared.api.PageResponse;

@Service
public class SaleQueryService {

	private final SaleRepository saleRepository;
	private final SaleItemRepository saleItemRepository;
	private final ProductRepository productRepository;

	public SaleQueryService(
			SaleRepository saleRepository,
			SaleItemRepository saleItemRepository,
			ProductRepository productRepository) {
		this.saleRepository = saleRepository;
		this.saleItemRepository = saleItemRepository;
		this.productRepository = productRepository;
	}

	public PageResponse<SaleSummary> list(UUID productId, LocalDateTime from, LocalDateTime to, int page, int size) {
		Page<SaleEntity> result = saleRepository.searchPublished(
				productId, boundFrom(from), boundTo(to), PageRequest.of(page, size));
		List<SaleSummary> content = result.getContent().stream()
				.map(sale -> new SaleSummary(
						sale.getId(),
						sale.getExternalSource(),
						sale.getExternalSaleId(),
						iso(sale.getOccurredAt()),
						decimal(saleItemRepository.sumPublishedTotalForSale(sale.getId()))))
				.toList();
		return PageResponse.of(content, page, size, result.getTotalElements());
	}

	public SaleDetail get(UUID id) {
		SaleEntity sale = saleRepository.findPublishedById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sale not found"));
		List<SaleItemDto> items = saleItemRepository.findPublishedBySaleId(id).stream()
				.map(this::toItem)
				.toList();
		return new SaleDetail(
				sale.getId(),
				sale.getExternalSource(),
				sale.getExternalSaleId(),
				iso(sale.getOccurredAt()),
				items);
	}

	private SaleItemDto toItem(SaleItemEntity item) {
		ProductEntity product = productRepository.findById(item.getProductId()).orElseThrow();
		return new SaleItemDto(
				item.getId(),
				item.getProductId(),
				product.getName(),
				product.getExternalId(),
				item.getSourceRecordIndex(),
				decimal(item.getQuantity()),
				decimal(item.getUnitPrice()),
				decimal(item.getDiscountPercentage()),
				decimal(item.getTotal()));
	}

	private static String iso(LocalDateTime value) {
		return value == null ? null : value.toString();
	}

	private static String decimal(BigDecimal value) {
		return value == null ? null : value.toPlainString();
	}

	static LocalDateTime boundFrom(LocalDateTime from) {
		return from == null ? LocalDateTime.of(1, 1, 1, 0, 0) : from;
	}

	static LocalDateTime boundTo(LocalDateTime to) {
		return to == null ? LocalDateTime.of(9999, 12, 31, 23, 59, 59) : to;
	}
}
