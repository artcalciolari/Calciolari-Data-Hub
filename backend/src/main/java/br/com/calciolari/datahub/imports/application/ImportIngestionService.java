package br.com.calciolari.datahub.imports.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductEntity;
import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductRepository;
import br.com.calciolari.datahub.imports.domain.hints.FilenameHints;
import br.com.calciolari.datahub.imports.domain.hints.FilenameHintsParser;
import br.com.calciolari.datahub.imports.domain.parser.ImportParser;
import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.IssueStage;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;
import br.com.calciolari.datahub.imports.domain.parser.ParseIssue;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.ParserInput;
import br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp.InterPdvQrpParser;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParsedMovementEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParsedMovementRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ValidationResultEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ValidationResultRepository;
import br.com.calciolari.datahub.imports.infrastructure.storage.LocalRawFileStorage;
import br.com.calciolari.datahub.imports.infrastructure.storage.RawFileDescriptor;
import br.com.calciolari.datahub.imports.infrastructure.storage.RawFileStorage;
import br.com.calciolari.datahub.imports.infrastructure.storage.StoredRawFile;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleEntity;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemEntity;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemRepository;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;

/**
 * Ingests one QRP upload: hash → immutable raw store → dedup → parse → optional publish.
 */
@Service
public class ImportIngestionService {

	private static final String SOURCE = "INTERPDV";
	private static final Pattern KV = Pattern.compile("(\\w+)=([^\\s]+)");

	private final RawFileStorage rawFileStorage;
	private final ImportParser parser;
	private final FilenameHintsParser hintsParser;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	private final RawArtifactRepository rawArtifactRepository;
	private final ImportJobRepository importJobRepository;
	private final ImportFileRepository importFileRepository;
	private final ParseAttemptRepository parseAttemptRepository;
	private final ArtifactPublicationRepository artifactPublicationRepository;
	private final ParsedMovementRepository parsedMovementRepository;
	private final ValidationResultRepository validationResultRepository;
	private final ProductRepository productRepository;
	private final SaleRepository saleRepository;
	private final SaleItemRepository saleItemRepository;

	public ImportIngestionService(
			RawFileStorage rawFileStorage,
			ImportParser parser,
			FilenameHintsParser hintsParser,
			ObjectMapper objectMapper,
			TransactionTemplate transactionTemplate,
			RawArtifactRepository rawArtifactRepository,
			ImportJobRepository importJobRepository,
			ImportFileRepository importFileRepository,
			ParseAttemptRepository parseAttemptRepository,
			ArtifactPublicationRepository artifactPublicationRepository,
			ParsedMovementRepository parsedMovementRepository,
			ValidationResultRepository validationResultRepository,
			ProductRepository productRepository,
			SaleRepository saleRepository,
			SaleItemRepository saleItemRepository) {
		this.rawFileStorage = rawFileStorage;
		this.parser = parser;
		this.hintsParser = hintsParser;
		this.objectMapper = objectMapper;
		this.transactionTemplate = transactionTemplate;
		this.rawArtifactRepository = rawArtifactRepository;
		this.importJobRepository = importJobRepository;
		this.importFileRepository = importFileRepository;
		this.parseAttemptRepository = parseAttemptRepository;
		this.artifactPublicationRepository = artifactPublicationRepository;
		this.parsedMovementRepository = parsedMovementRepository;
		this.validationResultRepository = validationResultRepository;
		this.productRepository = productRepository;
		this.saleRepository = saleRepository;
		this.saleItemRepository = saleItemRepository;
	}

	public ImportedFileResult ingest(InputStream content, String originalFilename) {
		UUID jobId = createJob();
		ImportedFileResult result = ingestIntoJob(jobId, content, originalFilename);
		completeJob(jobId);
		ImportJobEntity job = importJobRepository.findById(jobId).orElseThrow();
		return new ImportedFileResult(
				result.jobId(),
				result.importFileId(),
				result.rawArtifactId(),
				result.parseAttemptId(),
				result.sha256(),
				result.originalFilename(),
				result.deduplicated(),
				result.published(),
				job.getStatus(),
				result.fileStatus(),
				result.parseStatus(),
				result.recordsFound(),
				result.parsedQuantityTotal(),
				result.parsedRevenueTotal());
	}

	public UUID createJob() {
		return transactionTemplate.execute(status -> {
			ImportJobEntity job = importJobRepository.save(new ImportJobEntity(UUID.randomUUID(), "PROCESSING"));
			return job.getId();
		});
	}

	public void completeJob(UUID jobId) {
		transactionTemplate.executeWithoutResult(status -> {
			ImportJobEntity job = importJobRepository.findById(jobId).orElseThrow();
			List<ImportFileEntity> files = importFileRepository.findByImportJobIdOrderByCreatedAtAsc(jobId);
			long imported = files.stream().filter(f -> "IMPORTED".equals(f.getStatus()) || "WARNING".equals(f.getStatus())).count();
			long failed = files.stream().filter(f -> "INVALID".equals(f.getStatus()) || "FAILED".equals(f.getStatus())).count();
			if (files.isEmpty() || (failed > 0 && imported == 0)) {
				job.setStatus("FAILED");
			}
			else if (failed > 0) {
				job.setStatus("PARTIAL_SUCCESS");
			}
			else {
				job.setStatus("SUCCEEDED");
			}
			job.setCompletedAt(Instant.now());
			importJobRepository.save(job);
		});
	}

	public ImportedFileResult ingestIntoJob(UUID jobId, InputStream content, String originalFilename) {
		Objects.requireNonNull(jobId, "jobId");
		Objects.requireNonNull(content, "content");
		String filename = originalFilename == null ? "" : originalFilename;
		SpoolResult spool = spoolAndHash(content);

		StoredRawFile stored = rawFileStorage.putIfAbsent(
				new ByteArrayInputStream(spool.bytes()),
				new RawFileDescriptor(spool.sha256(), spool.bytes().length, "QRP"));

		FilenameHints hints = hintsParser.parse(filename);
		String hintsJson = writeHintsJson(hints);

		IngestContext ctx = null;
		DataIntegrityViolationException lastConflict = null;
		for (int attempt = 0; attempt < 5; attempt++) {
			try {
				ctx = transactionTemplate.execute(status -> openOrCreateOnce(jobId, spool, stored, filename, hintsJson));
				break;
			}
			catch (DataIntegrityViolationException ex) {
				lastConflict = ex;
			}
		}
		if (ctx == null) {
			throw new IllegalStateException(
					"failed to open/create artifact after retries for " + spool.sha256(), lastConflict);
		}
		final IngestContext ingestContext = ctx;

		if (ingestContext.skipParse()) {
			return toResult(ingestContext, null, ingestContext.alreadyPublished());
		}

		ParsedImport parsed;
		try (InputStream in = rawFileStorage.openVerified(
				stored.storageKey(), spool.sha256(), spool.bytes().length)) {
			parsed = parser.parse(new ParserInput(in, spool.bytes().length, filename, "QRP"));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

		return transactionTemplate.execute(status -> finalizeParse(ingestContext, parsed));
	}

	private IngestContext openOrCreateOnce(
			UUID jobId,
			SpoolResult spool,
			StoredRawFile stored,
			String filename,
			String hintsJson) {
		ImportJobEntity job = importJobRepository.findById(jobId)
				.orElseThrow(() -> new IllegalArgumentException("unknown job " + jobId));

		Optional<RawArtifactEntity> existing = rawArtifactRepository.findBySha256(spool.sha256());
		RawArtifactEntity artifact = existing.orElseGet(() -> rawArtifactRepository.save(new RawArtifactEntity(
				UUID.randomUUID(), spool.sha256(), spool.bytes().length, stored.storageKey(), "QRP")));

		ImportFileEntity file = new ImportFileEntity(
				UUID.randomUUID(), job.getId(), artifact.getId(), filename, SOURCE, "PENDING");
		file.setFilenameHints(hintsJson);

		Optional<ParseAttemptEntity> latest = parseAttemptRepository
				.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(
						artifact.getId(), InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION);

		if (latest.isPresent()) {
			ParseAttemptEntity attempt = latest.get();
			boolean published = artifactPublicationRepository.findByRawArtifactId(artifact.getId()).isPresent();
			file.setParseAttemptId(attempt.getId());
			file.setDeduplicated(true);
			Optional<ImportFileEntity> original = importFileRepository
					.findFirstByRawArtifactIdAndDeduplicatedFalseOrderByCreatedAtAsc(artifact.getId());
			original.ifPresent(o -> file.setDuplicateOfImportFileId(o.getId()));

			if ("PENDING".equals(attempt.getStatus()) || "PROCESSING".equals(attempt.getStatus())) {
				file.setStatus("PROCESSING");
				importFileRepository.save(file);
				return new IngestContext(job, file, artifact, attempt, true, published);
			}
			if ("VALID".equals(attempt.getStatus()) || "WARNING".equals(attempt.getStatus())) {
				file.setStatus(published ? "IMPORTED" : mapFileStatus(attempt.getStatus()));
				file.setCompletedAt(Instant.now());
				importFileRepository.save(file);
				return new IngestContext(job, file, artifact, attempt, true, published);
			}
			if ("INVALID".equals(attempt.getStatus())) {
				file.setStatus("INVALID");
				file.setCompletedAt(Instant.now());
				importFileRepository.save(file);
				return new IngestContext(job, file, artifact, attempt, true, false);
			}
			// FAILED — create new attempt count
		}

		int nextCount = latest.map(a -> a.getAttemptCount() + 1).orElse(1);
		ParseAttemptEntity attempt = new ParseAttemptEntity(
				UUID.randomUUID(),
				artifact.getId(),
				InterPdvQrpParser.PARSER_NAME,
				InterPdvQrpParser.PARSER_VERSION,
				"PROCESSING",
				nextCount);
		attempt.setStartedAt(Instant.now());
		attempt.setLeaseOwner("ingest-" + UUID.randomUUID());
		attempt.setLeaseGeneration(1L);
		attempt.setLeaseUntil(Instant.now().plusSeconds(300));
		parseAttemptRepository.save(attempt);

		file.setParseAttemptId(attempt.getId());
		file.setStatus("PROCESSING");
		file.setDeduplicated(existing.isPresent());
		if (existing.isPresent()) {
			importFileRepository.findFirstByRawArtifactIdAndDeduplicatedFalseOrderByCreatedAtAsc(artifact.getId())
					.ifPresent(o -> file.setDuplicateOfImportFileId(o.getId()));
		}
		importFileRepository.save(file);
		return new IngestContext(job, file, artifact, attempt, false, false);
	}

	private ImportedFileResult finalizeParse(IngestContext ctx, ParsedImport parsed) {
		ParseAttemptEntity attempt = parseAttemptRepository.findById(ctx.attempt().getId()).orElseThrow();
		ImportFileEntity file = importFileRepository.findById(ctx.file().getId()).orElseThrow();
		ImportJobEntity job = importJobRepository.findById(ctx.job().getId()).orElseThrow();

		for (ParsedMovement movement : parsed.movements()) {
			parsedMovementRepository.save(ParsedMovementEntity.from(UUID.randomUUID(), attempt.getId(), movement));
		}
		for (ParseIssue issue : parsed.issues()) {
			if (issue.stage() == IssueStage.VALIDATION) {
				validationResultRepository.save(toValidationEntity(attempt.getId(), issue));
			}
		}

		boolean blocking = parsed.hasFatalOrError();
		boolean published = false;
		String parseStatus;
		if (blocking) {
			parseStatus = parsed.issues().stream().anyMatch(i -> i.severity() == IssueSeverity.FATAL)
					? "FAILED"
					: "INVALID";
		}
		else {
			List<String> saleIds = parsed.movements().stream()
					.filter(m -> m.direction() == MovementDirection.OUT && m.externalSaleId() != null)
					.map(ParsedMovement::externalSaleId)
					.distinct()
					.toList();
			boolean overlap = !saleIds.isEmpty()
					&& artifactPublicationRepository.existsOverlappingPublishedSales(ctx.artifact().getId(), saleIds);
			if (overlap) {
				parseStatus = "WARNING";
				validationResultRepository.save(new ValidationResultEntity(
						UUID.randomUUID(),
						attempt.getId(),
						"OVERLAPPING_REPORT",
						"WARNING",
						null,
						null,
						null,
						null,
						"identity-v1",
						"canonical publication blocked"));
			}
			else {
				publishCanonical(ctx.artifact().getId(), attempt.getId(), parsed);
				published = true;
				parseStatus = parsed.issues().stream().anyMatch(i -> i.severity() == IssueSeverity.WARNING)
						? "WARNING"
						: "VALID";
			}
		}

		attempt.setStatus(parseStatus);
		attempt.setRecordsFound(parsed.movements().size());
		attempt.setCompletedAt(Instant.now());
		attempt.setLeaseUntil(null);
		parseAttemptRepository.save(attempt);

		file.setStatus(published ? "IMPORTED" : mapFileStatus(parseStatus));
		file.setCompletedAt(Instant.now());
		importFileRepository.save(file);

		return toResult(new IngestContext(job, file, ctx.artifact(), attempt, ctx.skipParse(), published), parsed, published);
	}

	private void publishCanonical(UUID artifactId, UUID attemptId, ParsedImport parsed) {
		if (parsed.externalProductId() == null) {
			return;
		}
		ProductEntity product = productRepository
				.findByExternalSourceAndExternalId(SOURCE, parsed.externalProductId())
				.orElseGet(() -> productRepository.save(new ProductEntity(
						UUID.randomUUID(),
						SOURCE,
						parsed.externalProductId(),
						parsed.productName() == null ? parsed.externalProductId() : parsed.productName(),
						attemptId)));
		if (parsed.productName() != null && !parsed.productName().equals(product.getName())) {
			product.setName(parsed.productName());
			productRepository.save(product);
		}

		for (ParsedMovement movement : parsed.movements()) {
			if (movement.direction() != MovementDirection.OUT) {
				continue;
			}
			if (movement.externalSaleId() == null || movement.quantity() == null
					|| movement.unitPrice() == null || movement.total() == null) {
				continue;
			}
			SaleEntity sale = saleRepository
					.findByExternalSourceAndExternalSaleId(SOURCE, movement.externalSaleId())
					.orElseGet(() -> saleRepository.save(new SaleEntity(
							UUID.randomUUID(),
							SOURCE,
							movement.externalSaleId(),
							movement.occurredAt(),
							attemptId)));
			saleItemRepository.save(new SaleItemEntity(
					UUID.randomUUID(),
					sale.getId(),
					product.getId(),
					attemptId,
					movement.sourceRecordIndex(),
					movement.quantity(),
					movement.unitPrice(),
					movement.discountPercentage(),
					movement.total(),
					movement.previousStock(),
					movement.resultingStock()));
		}

		ArtifactPublicationEntity publication = artifactPublicationRepository
				.findByRawArtifactId(artifactId)
				.orElseGet(() -> new ArtifactPublicationEntity(artifactId, attemptId));
		publication.setActiveParseAttemptId(attemptId);
		publication.setPublishedAt(Instant.now());
		artifactPublicationRepository.save(publication);
	}

	private static ValidationResultEntity toValidationEntity(UUID attemptId, ParseIssue issue) {
		String status = switch (issue.severity()) {
			case INFO -> "VALID";
			case WARNING -> "WARNING";
			case ERROR, FATAL -> "INVALID";
		};
		BigDecimal source = null;
		BigDecimal calculated = null;
		BigDecimal difference = null;
		BigDecimal tolerance = null;
		String ruleVersion = InterPdvQrpParser.PARSER_VERSION;
		Matcher matcher = KV.matcher(issue.message());
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = matcher.group(2);
			switch (key) {
				case "sourceValue" -> source = new BigDecimal(value);
				case "calculatedValue" -> calculated = new BigDecimal(value);
				case "difference" -> difference = new BigDecimal(value);
				case "tolerance" -> tolerance = new BigDecimal(value);
				case "ruleVersion" -> ruleVersion = value;
				default -> {
				}
			}
		}
		String locator = issue.sourceLocator() == null ? null : String.valueOf(issue.sourceLocator());
		return new ValidationResultEntity(
				UUID.randomUUID(),
				attemptId,
				issue.code(),
				status,
				source,
				calculated,
				difference,
				tolerance,
				ruleVersion,
				locator);
	}

	private String writeHintsJson(FilenameHints hints) {
		try {
			return objectMapper.writeValueAsString(hints);
		}
		catch (RuntimeException ex) {
			return "{\"originalFilename\":\"" + hints.originalFilename().replace("\"", "\\\"") + "\"}";
		}
	}

	private static String mapFileStatus(String parseStatus) {
		return switch (parseStatus) {
			case "VALID" -> "IMPORTED";
			case "WARNING" -> "WARNING";
			case "INVALID" -> "INVALID";
			default -> "FAILED";
		};
	}

	private static ImportedFileResult toResult(IngestContext ctx, ParsedImport parsed, boolean published) {
		return new ImportedFileResult(
				ctx.job().getId(),
				ctx.file().getId(),
				ctx.artifact().getId(),
				ctx.attempt().getId(),
				ctx.artifact().getSha256(),
				ctx.file().getOriginalFilename(),
				ctx.file().isDeduplicated(),
				published,
				ctx.job().getStatus(),
				ctx.file().getStatus(),
				ctx.attempt().getStatus(),
				parsed != null ? parsed.movements().size() : Optional.ofNullable(ctx.attempt().getRecordsFound()).orElse(0),
				parsed != null ? parsed.totals().parsedQuantityTotal() : null,
				parsed != null ? parsed.totals().parsedRevenueTotal() : null);
	}

	private static SpoolResult spoolAndHash(InputStream content) {
		try {
			Path temp = Files.createTempFile("datahub-upload-", ".qrp");
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (InputStream in = new DigestInputStream(content, digest);
					OutputStream out = Files.newOutputStream(temp)) {
				in.transferTo(out);
			}
			byte[] bytes = Files.readAllBytes(temp);
			Files.deleteIfExists(temp);
			String sha = HexFormat.of().formatHex(digest.digest()).toLowerCase(Locale.ROOT);
			return new SpoolResult(bytes, sha);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record SpoolResult(byte[] bytes, String sha256) {
	}

	private record IngestContext(
			ImportJobEntity job,
			ImportFileEntity file,
			RawArtifactEntity artifact,
			ParseAttemptEntity attempt,
			boolean skipParse,
			boolean alreadyPublished) {
	}
}
