package br.com.calciolari.datahub.sales.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sale_item")
public class SaleItemEntity {

	@Id
	private UUID id;

	@Column(name = "sale_id", nullable = false)
	private UUID saleId;

	@Column(name = "product_id", nullable = false)
	private UUID productId;

	@Column(name = "parse_attempt_id", nullable = false)
	private UUID parseAttemptId;

	@Column(name = "source_record_index", nullable = false)
	private int sourceRecordIndex;

	@Column(nullable = false)
	private BigDecimal quantity;

	@Column(name = "unit_price", nullable = false)
	private BigDecimal unitPrice;

	@Column(name = "discount_percentage")
	private BigDecimal discountPercentage;

	@Column(nullable = false)
	private BigDecimal total;

	@Column(name = "previous_stock")
	private BigDecimal previousStock;

	@Column(name = "resulting_stock")
	private BigDecimal resultingStock;

	protected SaleItemEntity() {
	}

	public SaleItemEntity(
			UUID id,
			UUID saleId,
			UUID productId,
			UUID parseAttemptId,
			int sourceRecordIndex,
			BigDecimal quantity,
			BigDecimal unitPrice,
			BigDecimal discountPercentage,
			BigDecimal total,
			BigDecimal previousStock,
			BigDecimal resultingStock) {
		this.id = id;
		this.saleId = saleId;
		this.productId = productId;
		this.parseAttemptId = parseAttemptId;
		this.sourceRecordIndex = sourceRecordIndex;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
		this.discountPercentage = discountPercentage;
		this.total = total;
		this.previousStock = previousStock;
		this.resultingStock = resultingStock;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSaleId() {
		return saleId;
	}

	public UUID getProductId() {
		return productId;
	}

	public UUID getParseAttemptId() {
		return parseAttemptId;
	}

	public int getSourceRecordIndex() {
		return sourceRecordIndex;
	}

	public BigDecimal getTotal() {
		return total;
	}
}
