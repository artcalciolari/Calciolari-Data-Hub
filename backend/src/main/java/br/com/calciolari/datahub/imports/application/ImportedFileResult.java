package br.com.calciolari.datahub.imports.application;

import java.math.BigDecimal;
import java.util.UUID;

public record ImportedFileResult(
		UUID jobId,
		UUID importFileId,
		UUID rawArtifactId,
		UUID parseAttemptId,
		String sha256,
		String originalFilename,
		boolean deduplicated,
		boolean published,
		String jobStatus,
		String fileStatus,
		String parseStatus,
		int recordsFound,
		BigDecimal parsedQuantityTotal,
		BigDecimal parsedRevenueTotal
) {
}
