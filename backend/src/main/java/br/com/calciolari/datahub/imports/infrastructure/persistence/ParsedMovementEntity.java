package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parsed_movement")
public class ParsedMovementEntity {

	@Id
	private UUID id;

	@Column(name = "parse_attempt_id", nullable = false)
	private UUID parseAttemptId;

	@Column(name = "source_record_index", nullable = false)
	private int sourceRecordIndex;

	@Column(nullable = false)
	private String direction;

	@Column(name = "external_product_id")
	private String externalProductId;

	@Column(name = "product_name")
	private String productName;

	@Column(name = "external_sale_id")
	private String externalSaleId;

	@Column(name = "occurred_at")
	private LocalDateTime occurredAt;

	private BigDecimal quantity;

	@Column(name = "unit_price")
	private BigDecimal unitPrice;

	@Column(name = "discount_percentage")
	private BigDecimal discountPercentage;

	private BigDecimal total;

	@Column(name = "previous_stock")
	private BigDecimal previousStock;

	@Column(name = "resulting_stock")
	private BigDecimal resultingStock;

	private String manufacturer;

	@Column(name = "source_locator")
	private String sourceLocator;

	protected ParsedMovementEntity() {
	}

	public static ParsedMovementEntity from(
			UUID id,
			UUID parseAttemptId,
			br.com.calciolari.datahub.imports.domain.parser.ParsedMovement movement) {
		ParsedMovementEntity entity = new ParsedMovementEntity();
		entity.id = id;
		entity.parseAttemptId = parseAttemptId;
		entity.sourceRecordIndex = movement.sourceRecordIndex();
		entity.direction = movement.direction().name();
		entity.externalProductId = movement.externalProductId();
		entity.productName = movement.productName();
		entity.externalSaleId = movement.externalSaleId();
		entity.occurredAt = movement.occurredAt();
		entity.quantity = movement.quantity();
		entity.unitPrice = movement.unitPrice();
		entity.discountPercentage = movement.discountPercentage();
		entity.total = movement.total();
		entity.previousStock = movement.previousStock();
		entity.resultingStock = movement.resultingStock();
		entity.manufacturer = movement.manufacturer();
		if (movement.sourceLocator() != null) {
			entity.sourceLocator = String.valueOf(movement.sourceLocator());
		}
		return entity;
	}

	public UUID getId() {
		return id;
	}

	public UUID getParseAttemptId() {
		return parseAttemptId;
	}

	public int getSourceRecordIndex() {
		return sourceRecordIndex;
	}

	public String getDirection() {
		return direction;
	}

	public String getExternalProductId() {
		return externalProductId;
	}

	public String getProductName() {
		return productName;
	}

	public String getExternalSaleId() {
		return externalSaleId;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public BigDecimal getDiscountPercentage() {
		return discountPercentage;
	}

	public BigDecimal getTotal() {
		return total;
	}
}
