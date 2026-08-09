package br.com.calciolari.datahub.imports.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ImportDtos {

	private ImportDtos() {
	}

	public record ImportJobResponse(
			UUID id,
			String status,
			Instant createdAt,
			Instant completedAt,
			List<ImportFileSummary> files
	) {
	}

	public record ImportFileSummary(
			UUID id,
			String originalFilename,
			String status,
			boolean deduplicated,
			UUID duplicateOfImportFileId,
			UUID parseAttemptId,
			Instant createdAt,
			Instant completedAt
	) {
	}

	public record ImportFileDetail(
			UUID id,
			UUID jobId,
			UUID rawArtifactId,
			UUID parseAttemptId,
			String originalFilename,
			String source,
			String status,
			boolean deduplicated,
			UUID duplicateOfImportFileId,
			String sha256,
			Long byteSize,
			String parseStatus,
			Integer recordsFound,
			String parserName,
			String parserVersion,
			Object filenameHints,
			List<ValidationDto> validations,
			Instant createdAt,
			Instant completedAt
	) {
	}

	public record ValidationDto(
			String code,
			String status,
			String sourceValue,
			String calculatedValue,
			String difference,
			String tolerance,
			String ruleVersion,
			String sourceLocator
	) {
		public static String decimal(BigDecimal value) {
			return value == null ? null : value.toPlainString();
		}
	}

	public record ImportAcceptedResponse(
			UUID jobId,
			String status,
			List<ImportFileSummary> files
	) {
	}
}
