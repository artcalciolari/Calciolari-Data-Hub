package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "validation_result")
public class ValidationResultEntity {

	@Id
	private UUID id;

	@Column(name = "parse_attempt_id", nullable = false)
	private UUID parseAttemptId;

	@Column(nullable = false)
	private String code;

	@Column(nullable = false)
	private String status;

	@Column(name = "source_value")
	private BigDecimal sourceValue;

	@Column(name = "calculated_value")
	private BigDecimal calculatedValue;

	private BigDecimal difference;

	private BigDecimal tolerance;

	@Column(name = "rule_version", nullable = false)
	private String ruleVersion;

	@Column(name = "source_locator")
	private String sourceLocator;

	protected ValidationResultEntity() {
	}

	public ValidationResultEntity(
			UUID id,
			UUID parseAttemptId,
			String code,
			String status,
			BigDecimal sourceValue,
			BigDecimal calculatedValue,
			BigDecimal difference,
			BigDecimal tolerance,
			String ruleVersion,
			String sourceLocator) {
		this.id = id;
		this.parseAttemptId = parseAttemptId;
		this.code = code;
		this.status = status;
		this.sourceValue = sourceValue;
		this.calculatedValue = calculatedValue;
		this.difference = difference;
		this.tolerance = tolerance;
		this.ruleVersion = ruleVersion;
		this.sourceLocator = sourceLocator;
	}

	public UUID getId() {
		return id;
	}

	public UUID getParseAttemptId() {
		return parseAttemptId;
	}

	public String getCode() {
		return code;
	}

	public String getStatus() {
		return status;
	}
}
