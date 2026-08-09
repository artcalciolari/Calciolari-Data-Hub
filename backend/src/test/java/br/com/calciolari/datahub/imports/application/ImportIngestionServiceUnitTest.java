package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

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
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportStats;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportTotals;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.SourceLocator;
import br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp.InterPdvQrpParser;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParsedMovementRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ValidationResultRepository;
import br.com.calciolari.datahub.imports.infrastructure.storage.RawFileDescriptor;
import br.com.calciolari.datahub.imports.infrastructure.storage.RawFileStorage;
import br.com.calciolari.datahub.imports.infrastructure.storage.RawStorageIntegrityException;
import br.com.calciolari.datahub.imports.infrastructure.storage.StoredRawFile;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemRepository;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;
import tools.jackson.databind.ObjectMapper;

class ImportIngestionServiceUnitTest {

	RawFileStorage storage;
	ImportParser parser;
	FilenameHintsParser hintsParser;
	ObjectMapper objectMapper;
	TransactionTemplate tx;
	RawArtifactRepository rawArtifacts;
	ImportJobRepository jobs;
	ImportFileRepository files;
	ParseAttemptRepository attempts;
	ArtifactPublicationRepository publications;
	ParsedMovementRepository movements;
	ValidationResultRepository validations;
	ProductRepository products;
	SaleRepository sales;
	SaleItemRepository saleItems;
	ImportIngestionService service;

	@BeforeEach
	void setUp() {
		storage = mock(RawFileStorage.class);
		parser = mock(ImportParser.class);
		hintsParser = mock(FilenameHintsParser.class);
		objectMapper = mock(ObjectMapper.class);
		tx = mock(TransactionTemplate.class);
		rawArtifacts = mock(RawArtifactRepository.class);
		jobs = mock(ImportJobRepository.class);
		files = mock(ImportFileRepository.class);
		attempts = mock(ParseAttemptRepository.class);
		publications = mock(ArtifactPublicationRepository.class);
		movements = mock(ParsedMovementRepository.class);
		validations = mock(ValidationResultRepository.class);
		products = mock(ProductRepository.class);
		sales = mock(SaleRepository.class);
		saleItems = mock(SaleItemRepository.class);
		stubTx(tx);
		when(hintsParser.parse(any())).thenReturn(FilenameHints.empty("f.qrp"));
		when(objectMapper.writeValueAsString(any())).thenReturn("{}");
		service = new ImportIngestionService(
				storage, parser, hintsParser, objectMapper, tx,
				rawArtifacts, jobs, files, attempts, publications, movements, validations,
				products, sales, saleItems);
	}

	@Test
	void completeJobStatuses() {
		UUID jobId = UUID.randomUUID();
		ImportJobEntity job = new ImportJobEntity(jobId, "PROCESSING");
		when(jobs.findById(jobId)).thenReturn(Optional.of(job));
		when(files.findByImportJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of());
		service.completeJob(jobId);
		assertEquals("FAILED", job.getStatus());

		when(files.findByImportJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of(file(jobId, "INVALID")));
		service.completeJob(jobId);
		assertEquals("FAILED", job.getStatus());

		when(files.findByImportJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of(
				file(jobId, "IMPORTED"), file(jobId, "WARNING"), file(jobId, "FAILED")));
		service.completeJob(jobId);
		assertEquals("PARTIAL_SUCCESS", job.getStatus());

		when(files.findByImportJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of(
				file(jobId, "IMPORTED"), file(jobId, "WARNING")));
		service.completeJob(jobId);
		assertEquals("SUCCEEDED", job.getStatus());
	}

	@Test
	void ingestHappyPathHintsFallbackAndValidationKv() {
		when(jobs.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(jobs.findById(any())).thenAnswer(inv -> Optional.of(new ImportJobEntity(inv.getArgument(0), "PROCESSING")));
		when(files.findByImportJobIdOrderByCreatedAtAsc(any())).thenAnswer(inv -> List.of(file(inv.getArgument(0), "IMPORTED")));
		stubNewArtifactIngest();
		when(parser.parse(any())).thenReturn(validParsed(List.of(outSale("S1")), List.of(
				new ParseIssue("V", IssueSeverity.WARNING, IssueStage.VALIDATION, null,
						"sourceValue=1 calculatedValue=2 difference=1 tolerance=0.1 ruleVersion=rv other=x"))));
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		stubPublishHappy();
		when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("json"));

		ImportedFileResult result = service.ingest(new ByteArrayInputStream(new byte[] {1, 2, 3}), null);
		assertTrue(result.published());
		assertEquals("WARNING", result.parseStatus());
		verify(validations, atLeastOnce()).save(any());
	}

	@Test
	void openCreateRetriesExhausted() {
		UUID jobId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		when(storage.putIfAbsent(any(), any())).thenAnswer(this::storedFromDescriptor);
		when(rawArtifacts.findBySha256(anyString())).thenThrow(new DataIntegrityViolationException("dup"));
		assertThrows(IllegalStateException.class,
				() -> service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {9}), "x.qrp"));
	}

	@Test
	void skipParseBranches() {
		UUID jobId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		byte[] bytes = new byte[] {1};
		when(storage.putIfAbsent(any(), any())).thenAnswer(this::storedFromDescriptor);
		when(rawArtifacts.findBySha256(anyString())).thenAnswer(inv -> Optional.of(
				new RawArtifactEntity(artifactId, inv.getArgument(0), 1L, "00/00/" + inv.getArgument(0), "QRP")));
		when(files.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(files.findFirstByRawArtifactIdAndDeduplicatedFalseOrderByCreatedAtAsc(artifactId))
				.thenReturn(Optional.of(file(jobId, "IMPORTED")));

		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(eq(artifactId), any(), any()))
				.thenReturn(Optional.of(attempt(artifactId, "PENDING", 1)));
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());
		assertEquals("PROCESSING", service.ingestIntoJob(jobId, new ByteArrayInputStream(bytes), "d.qrp").fileStatus());

		ParseAttemptEntity valid = attempt(artifactId, "VALID", 1);
		valid.setRecordsFound(4);
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(eq(artifactId), any(), any()))
				.thenReturn(Optional.of(valid));
		when(publications.findByRawArtifactId(artifactId))
				.thenReturn(Optional.of(new ArtifactPublicationEntity(artifactId, valid.getId())));
		ImportedFileResult skipped = service.ingestIntoJob(jobId, new ByteArrayInputStream(bytes), "d.qrp");
		assertEquals("IMPORTED", skipped.fileStatus());
		assertEquals(4, skipped.recordsFound());

		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(eq(artifactId), any(), any()))
				.thenReturn(Optional.of(attempt(artifactId, "WARNING", 1)));
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());
		assertEquals("WARNING", service.ingestIntoJob(jobId, new ByteArrayInputStream(bytes), "d.qrp").fileStatus());

		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(eq(artifactId), any(), any()))
				.thenReturn(Optional.of(attempt(artifactId, "INVALID", 1)));
		assertEquals("INVALID", service.ingestIntoJob(jobId, new ByteArrayInputStream(bytes), "d.qrp").fileStatus());

		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(eq(artifactId), any(), any()))
				.thenReturn(Optional.of(attempt(artifactId, "FAILED", 2)));
		when(attempts.save(any())).thenAnswer(a -> a.getArgument(0));
		when(storage.openVerified(anyString(), anyString(), anyLong())).thenReturn(new ByteArrayInputStream(bytes));
		when(parser.parse(any())).thenReturn(validParsed(List.of(), List.of(
				new ParseIssue("F", IssueSeverity.FATAL, IssueStage.CONTAINER, SourceLocator.empty(), "fatal"))));
		when(files.findById(any())).thenAnswer(a -> Optional.of(file(jobId, "PROCESSING")));
		when(attempts.findById(any())).thenAnswer(a -> Optional.of(attempt(artifactId, "PROCESSING", 3)));
		assertEquals("FAILED", service.ingestIntoJob(jobId, new ByteArrayInputStream(bytes), "d.qrp").parseStatus());
	}

	@Test
	void finalizeParseOverlapErrorPublishEdges() {
		UUID jobId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		stubNewArtifactIngest();

		when(parser.parse(any())).thenReturn(validParsed(List.of(outSale("S9")), List.of()));
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(true);
		ImportedFileResult overlap = service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {5}), "o.qrp");
		assertEquals("WARNING", overlap.parseStatus());
		assertFalse(overlap.published());

		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		when(parser.parse(any())).thenReturn(validParsed(List.of(
				new ParsedMovement(0, MovementDirection.IN, "41", "N", null, null, null, null, null, null, null, null, null, SourceLocator.empty()),
				new ParsedMovement(1, MovementDirection.OUT, "41", "N", null, null, BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE, null, null, null, SourceLocator.empty()),
				outSale("S2")),
				List.of(new ParseIssue("I", IssueSeverity.INFO, IssueStage.MAPPING, SourceLocator.empty(), "info"))));
		ProductEntity existing = new ProductEntity(UUID.randomUUID(), "INTERPDV", "41", "OLD", UUID.randomUUID());
		when(products.findByExternalSourceAndExternalId(eq("INTERPDV"), eq("41"))).thenReturn(Optional.of(existing));
		when(sales.findByExternalSourceAndExternalSaleId(any(), any())).thenReturn(Optional.empty());
		when(sales.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(publications.findByRawArtifactId(any())).thenReturn(Optional.of(new ArtifactPublicationEntity(UUID.randomUUID(), UUID.randomUUID())));
		when(publications.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
		assertTrue(service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {5}), "p.qrp").published());
		verify(products, atLeastOnce()).save(existing);

		when(parser.parse(any())).thenReturn(new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				null, null, List.of(), ParsedImportTotals.empty(), ParsedImportStats.empty(),
				List.of(new ParseIssue("E", IssueSeverity.ERROR, IssueStage.VALIDATION, SourceLocator.empty(), "sourceValue=1"))));
		assertEquals("INVALID", service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {5}), "e.qrp").parseStatus());
	}

	@Test
	void ingestCloseIoFailure() {
		UUID jobId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		stubNewArtifactIngestWithoutOpen();
		InputStream broken = new InputStream() {
			@Override public int read() { return 1; }
			@Override public void close() throws IOException { throw new IOException("read"); }
		};
		when(storage.openVerified(anyString(), anyString(), anyLong())).thenReturn(broken);
		when(parser.parse(any())).thenReturn(validParsed(List.of(), List.of()));
		assertThrows(UncheckedIOException.class,
				() -> service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {1}), "x.qrp"));
	}

	@Test
	void reprocessAllBranches() {
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, UUID.randomUUID(), artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		RawArtifactEntity artifact = new RawArtifactEntity(artifactId, "f".repeat(64), 3, "00/00/" + "f".repeat(64), "QRP");

		when(files.findById(fileId)).thenReturn(Optional.empty());
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.reprocess(fileId)));

		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.empty());
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.reprocess(fileId)));

		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
		doAnswer(inv -> { throw new RawStorageIntegrityException("bad"); })
				.when(storage).openVerified(anyString(), anyString(), anyLong());
		assertEquals(HttpStatus.CONFLICT, status(() -> service.reprocess(fileId)));

		when(rawArtifacts.findWithLockById(artifactId)).thenReturn(Optional.of(artifact));
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.empty());
		when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());
		when(attempts.findById(any())).thenAnswer(inv -> Optional.of(new ParseAttemptEntity(
				inv.getArgument(0), artifactId, InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1)));

		AtomicInteger opens = new AtomicInteger();
		doAnswer(inv -> {
			if (opens.incrementAndGet() == 1) {
				return new ByteArrayInputStream(new byte[] {1, 2, 3});
			}
			return new ByteArrayInputStream(new byte[] {1}) {
				@Override public void close() throws IOException { throw new IOException("boom"); }
			};
		}).when(storage).openVerified(anyString(), anyString(), anyLong());
		when(parser.parse(any())).thenReturn(validParsed(List.of(), List.of()));
		assertThrows(UncheckedIOException.class, () -> service.reprocess(fileId));

		doAnswer(inv -> new ByteArrayInputStream(new byte[] {1, 2, 3}))
				.when(storage).openVerified(anyString(), anyString(), anyLong());
		doAnswer(inv -> { throw new IllegalStateException("parse-fail"); }).when(parser).parse(any());
		assertThrows(IllegalStateException.class, () -> service.reprocess(fileId));

		ParseAttemptEntity leased = attempt(artifactId, "PROCESSING", 1);
		leased.setLeaseUntil(Instant.now().plusSeconds(60));
		leased.setLeaseOwner("other");
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.of(leased));
		assertEquals(HttpStatus.CONFLICT, status(() -> service.reprocess(fileId)));

		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.empty());
		final String[] lease = new String[1];
		when(attempts.save(any())).thenAnswer(inv -> {
			ParseAttemptEntity a = inv.getArgument(0);
			if (a.getLeaseOwner() != null && a.getLeaseOwner().startsWith("reprocess-")) {
				lease[0] = a.getLeaseOwner();
			}
			return a;
		});
		when(attempts.findById(any())).thenAnswer(inv -> {
			ParseAttemptEntity real = new ParseAttemptEntity(inv.getArgument(0), artifactId,
					InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1);
			real.setLeaseOwner("wrong");
			return Optional.of(real);
		});
		org.mockito.Mockito.doReturn(validParsed(List.of(outSale("R1")), List.of())).when(parser).parse(any());
		assertEquals(HttpStatus.CONFLICT, status(() -> service.reprocess(fileId)));

		when(attempts.findById(any())).thenAnswer(inv -> {
			ParseAttemptEntity real = new ParseAttemptEntity(inv.getArgument(0), artifactId,
					InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1);
			real.setLeaseOwner(lease[0]);
			return Optional.of(real);
		});
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		stubPublishHappy();
		assertTrue(service.reprocess(fileId).published());

		org.mockito.Mockito.doReturn(validParsed(List.of(outSale("R2")), List.of(
				new ParseIssue("W", IssueSeverity.WARNING, IssueStage.VALIDATION, SourceLocator.empty(), "warn"))))
				.when(parser).parse(any());
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(true);
		ImportIngestionService.ReprocessResult warn = service.reprocess(fileId);
		assertEquals("WARNING", warn.parseStatus());
		assertFalse(warn.published());

		org.mockito.Mockito.doReturn(validParsed(List.of(), List.of(
				new ParseIssue("F", IssueSeverity.FATAL, IssueStage.CONTAINER, SourceLocator.empty(), "fatal"))))
				.when(parser).parse(any());
		ImportIngestionService.ReprocessResult fatal = service.reprocess(fileId);
		assertEquals("FAILED", fatal.parseStatus());

		opens.set(0);
		doAnswer(inv -> {
			if (opens.incrementAndGet() == 1) {
				return new ByteArrayInputStream(new byte[] {1});
			}
			throw new RawStorageIntegrityException("later");
		}).when(storage).openVerified(anyString(), anyString(), anyLong());
		assertEquals(HttpStatus.CONFLICT, status(() -> service.reprocess(fileId)));

		// claimReprocess missing lock
		doAnswer(inv -> new ByteArrayInputStream(new byte[] {1}))
				.when(storage).openVerified(anyString(), anyString(), anyLong());
		when(rawArtifacts.findWithLockById(artifactId)).thenReturn(Optional.empty());
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.reprocess(fileId)));
	}

	@Test
	void reprocessMissingInsideLock() {
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, UUID.randomUUID(), artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		RawArtifactEntity artifact = new RawArtifactEntity(artifactId, "a".repeat(64), 1, "00/00/" + "a".repeat(64), "QRP");
		when(files.findById(fileId)).thenReturn(Optional.of(file)).thenReturn(Optional.empty());
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.reprocess(fileId)));

		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact)).thenReturn(Optional.empty());
		assertEquals(HttpStatus.NOT_FOUND, status(() -> service.reprocess(fileId)));
	}

	@Test
	void publishCanonicalNullProductNameUsesExternalId() {
		UUID jobId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		stubNewArtifactIngest();
		when(parser.parse(any())).thenReturn(new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				"41", null, List.of(outSale("S3")), ParsedImportTotals.empty(), ParsedImportStats.empty(), List.of()));
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		when(products.findByExternalSourceAndExternalId(any(), any())).thenReturn(Optional.empty());
		when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(sales.findByExternalSourceAndExternalSaleId(any(), any())).thenReturn(Optional.empty());
		when(sales.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(publications.findByRawArtifactId(any())).thenReturn(Optional.empty());
		when(publications.save(any())).thenAnswer(inv -> inv.getArgument(0));
		assertTrue(service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {1}), "n.qrp").published());
	}

	@Test
	void failAttemptNullAndLongSummaryViaReprocessRuntime() {
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, UUID.randomUUID(), artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		RawArtifactEntity artifact = new RawArtifactEntity(artifactId, "b".repeat(64), 1, "00/00/" + "b".repeat(64), "QRP");
		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
		when(rawArtifacts.findWithLockById(artifactId)).thenReturn(Optional.of(artifact));
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.empty());
		when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());
		doAnswer(inv -> new ByteArrayInputStream(new byte[] {1}))
				.when(storage).openVerified(anyString(), anyString(), anyLong());

		when(attempts.findById(any())).thenReturn(Optional.empty());
		doAnswer(inv -> { throw new IllegalStateException("x".repeat(600)); }).when(parser).parse(any());
		assertThrows(IllegalStateException.class, () -> service.reprocess(fileId));

		when(attempts.findById(any())).thenAnswer(inv -> Optional.of(new ParseAttemptEntity(
				inv.getArgument(0), artifactId, InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1)));
		doAnswer(inv -> { throw new IllegalStateException((String) null); }).when(parser).parse(any());
		assertThrows(IllegalStateException.class, () -> service.reprocess(fileId));
	}

	@Test
	void claimReprocessAllowsExpiredOrNullLease() {
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, UUID.randomUUID(), artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		RawArtifactEntity artifact = new RawArtifactEntity(artifactId, "c".repeat(64), 1, "00/00/" + "c".repeat(64), "QRP");
		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
		when(rawArtifacts.findWithLockById(artifactId)).thenReturn(Optional.of(artifact));
		doAnswer(inv -> new ByteArrayInputStream(new byte[] {1}))
				.when(storage).openVerified(anyString(), anyString(), anyLong());
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());
		when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
		final String[] lease = new String[1];
		when(attempts.save(any())).thenAnswer(inv -> {
			ParseAttemptEntity a = inv.getArgument(0);
			if (a.getLeaseOwner() != null && a.getLeaseOwner().startsWith("reprocess-")) {
				lease[0] = a.getLeaseOwner();
			}
			return a;
		});
		when(attempts.findById(any())).thenAnswer(inv -> {
			ParseAttemptEntity real = new ParseAttemptEntity(inv.getArgument(0), artifactId,
					InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1);
			real.setLeaseOwner(lease[0]);
			return Optional.of(real);
		});
		org.mockito.Mockito.doReturn(validParsed(List.of(outSale("E1")), List.of())).when(parser).parse(any());
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		stubPublishHappy();

		ParseAttemptEntity expired = attempt(artifactId, "PROCESSING", 1);
		expired.setLeaseUntil(Instant.now().minusSeconds(60));
		expired.setLeaseOwner("old");
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.of(expired));
		assertTrue(service.reprocess(fileId).published());

		ParseAttemptEntity nullLease = attempt(artifactId, "PENDING", 2);
		nullLease.setLeaseUntil(null);
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.of(nullLease));
		assertTrue(service.reprocess(fileId).published());
	}

	@Test
	void publishUsesExistingSaleAndInfoValidation() {
		UUID jobId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		stubNewArtifactIngest();
		org.mockito.Mockito.doReturn(validParsed(List.of(outSale("EXISTING")), List.of(
				new ParseIssue("I", IssueSeverity.INFO, IssueStage.VALIDATION, SourceLocator.empty(), "plain"))))
				.when(parser).parse(any());
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		when(products.findByExternalSourceAndExternalId(any(), any())).thenReturn(Optional.empty());
		when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(sales.findByExternalSourceAndExternalSaleId(any(), any())).thenReturn(Optional.of(
				new br.com.calciolari.datahub.sales.infrastructure.persistence.SaleEntity(
						UUID.randomUUID(), "INTERPDV", "EXISTING", LocalDateTime.now(), UUID.randomUUID())));
		when(publications.findByRawArtifactId(any())).thenReturn(Optional.empty());
		when(publications.save(any())).thenAnswer(inv -> inv.getArgument(0));
		ImportedFileResult result = service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {1}), "e.qrp");
		assertTrue(result.published());
		assertEquals("VALID", result.parseStatus());
	}

	@Test
	void reprocessFinalizeWarningPublished() {
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, UUID.randomUUID(), artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		RawArtifactEntity artifact = new RawArtifactEntity(artifactId, "d".repeat(64), 1, "00/00/" + "d".repeat(64), "QRP");
		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
		when(rawArtifacts.findWithLockById(artifactId)).thenReturn(Optional.of(artifact));
		doAnswer(inv -> new ByteArrayInputStream(new byte[] {1}))
				.when(storage).openVerified(anyString(), anyString(), anyLong());
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.empty());
		final String[] lease = new String[1];
		when(attempts.save(any())).thenAnswer(inv -> {
			ParseAttemptEntity a = inv.getArgument(0);
			if (a.getLeaseOwner() != null && a.getLeaseOwner().startsWith("reprocess-")) {
				lease[0] = a.getLeaseOwner();
			}
			return a;
		});
		when(attempts.findById(any())).thenAnswer(inv -> {
			ParseAttemptEntity real = new ParseAttemptEntity(inv.getArgument(0), artifactId,
					InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1);
			real.setLeaseOwner(lease[0]);
			return Optional.of(real);
		});
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());
		org.mockito.Mockito.doReturn(validParsed(
				List.of(
						new ParsedMovement(0, MovementDirection.OUT, "41", "N", null, LocalDateTime.now(),
								BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE, null, null, null, SourceLocator.empty()),
						outSale("W1")),
				List.of(new ParseIssue("W", IssueSeverity.WARNING, IssueStage.MAPPING, SourceLocator.empty(), "warn"))))
				.when(parser).parse(any());
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		stubPublishHappy();
		ImportIngestionService.ReprocessResult result = service.reprocess(fileId);
		assertTrue(result.published());
		assertEquals("WARNING", result.parseStatus());
	}

	@Test
	void reprocessIntegrityIoExceptionOnFirstOpen() {
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, UUID.randomUUID(), artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		RawArtifactEntity artifact = new RawArtifactEntity(artifactId, "e".repeat(64), 1, "00/00/" + "e".repeat(64), "QRP");
		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
		doAnswer(inv -> { throw new IOException("disk"); })
				.when(storage).openVerified(anyString(), anyString(), anyLong());
		assertThrows(UncheckedIOException.class, () -> service.reprocess(fileId));
	}

	@Test
	void reprocessClaimPendingLeaseConflictAndInvalidError() {
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, UUID.randomUUID(), artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		RawArtifactEntity artifact = new RawArtifactEntity(artifactId, "f".repeat(64), 1, "00/00/" + "f".repeat(64), "QRP");
		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(rawArtifacts.findById(artifactId)).thenReturn(Optional.of(artifact));
		when(rawArtifacts.findWithLockById(artifactId)).thenReturn(Optional.of(artifact));
		doAnswer(inv -> new ByteArrayInputStream(new byte[] {1}))
				.when(storage).openVerified(anyString(), anyString(), anyLong());
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());

		ParseAttemptEntity pendingLeased = attempt(artifactId, "PENDING", 1);
		pendingLeased.setLeaseUntil(Instant.now().plusSeconds(120));
		pendingLeased.setLeaseOwner("holder");
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.of(pendingLeased));
		assertEquals(HttpStatus.CONFLICT, status(() -> service.reprocess(fileId)));

		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.empty());
		final String[] lease = new String[1];
		when(attempts.save(any())).thenAnswer(inv -> {
			ParseAttemptEntity a = inv.getArgument(0);
			if (a.getLeaseOwner() != null && a.getLeaseOwner().startsWith("reprocess-")) {
				lease[0] = a.getLeaseOwner();
			}
			return a;
		});
		when(attempts.findById(any())).thenAnswer(inv -> {
			ParseAttemptEntity real = new ParseAttemptEntity(inv.getArgument(0), artifactId,
					InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1);
			real.setLeaseOwner(lease[0]);
			return Optional.of(real);
		});
		org.mockito.Mockito.doReturn(validParsed(List.of(), List.of(
				new ParseIssue("E", IssueSeverity.ERROR, IssueStage.VALIDATION, SourceLocator.empty(), "err"))))
				.when(parser).parse(any());
		ImportIngestionService.ReprocessResult invalid = service.reprocess(fileId);
		assertEquals("INVALID", invalid.parseStatus());
		assertFalse(invalid.published());
	}

	@Test
	void publishSkipsIncompleteOutSalesAndNullLocatorIssue() {
		UUID jobId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		stubNewArtifactIngest();

		ParseIssue nullLocator = mock(ParseIssue.class);
		when(nullLocator.stage()).thenReturn(IssueStage.VALIDATION);
		when(nullLocator.severity()).thenReturn(IssueSeverity.INFO);
		when(nullLocator.code()).thenReturn("LOC");
		when(nullLocator.message()).thenReturn("plain");
		when(nullLocator.sourceLocator()).thenReturn(null);

		org.mockito.Mockito.doReturn(validParsed(List.of(
				new ParsedMovement(0, MovementDirection.OUT, "41", "N", "Q1", LocalDateTime.now(),
						null, BigDecimal.ONE, null, BigDecimal.ONE, null, null, null, SourceLocator.empty()),
				new ParsedMovement(1, MovementDirection.OUT, "41", "N", "Q2", LocalDateTime.now(),
						BigDecimal.ONE, null, null, BigDecimal.ONE, null, null, null, SourceLocator.empty()),
				new ParsedMovement(2, MovementDirection.OUT, "41", "N", "Q3", LocalDateTime.now(),
						BigDecimal.ONE, BigDecimal.ONE, null, null, null, null, null, SourceLocator.empty()),
				outSale("OK")),
				List.of(nullLocator))).when(parser).parse(any());
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(false);
		stubPublishHappy();
		ImportedFileResult result = service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {1}), "inc.qrp");
		assertTrue(result.published());
		verify(validations, atLeastOnce()).save(any());
	}

	@Test
	void skipParseProcessingAndNullRecordsFound() {
		UUID jobId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		byte[] bytes = new byte[] {7};
		when(storage.putIfAbsent(any(), any())).thenAnswer(this::storedFromDescriptor);
		when(rawArtifacts.findBySha256(anyString())).thenAnswer(inv -> Optional.of(
				new RawArtifactEntity(artifactId, inv.getArgument(0), 1L, "00/00/" + inv.getArgument(0), "QRP")));
		when(files.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(files.findFirstByRawArtifactIdAndDeduplicatedFalseOrderByCreatedAtAsc(artifactId))
				.thenReturn(Optional.empty());
		when(publications.findByRawArtifactId(artifactId)).thenReturn(Optional.empty());

		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(eq(artifactId), any(), any()))
				.thenReturn(Optional.of(attempt(artifactId, "PROCESSING", 1)));
		assertEquals("PROCESSING", service.ingestIntoJob(jobId, new ByteArrayInputStream(bytes), "p.qrp").fileStatus());

		ParseAttemptEntity valid = attempt(artifactId, "VALID", 1);
		valid.setRecordsFound(null);
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(eq(artifactId), any(), any()))
				.thenReturn(Optional.of(valid));
		ImportedFileResult skipped = service.ingestIntoJob(jobId, new ByteArrayInputStream(bytes), "p.qrp");
		assertEquals(0, skipped.recordsFound());
	}

	@Test
	void finalizeParseEmptySaleIdsStillPublishes() {
		UUID jobId = UUID.randomUUID();
		when(jobs.findById(jobId)).thenReturn(Optional.of(new ImportJobEntity(jobId, "PROCESSING")));
		stubNewArtifactIngest();
		org.mockito.Mockito.doReturn(validParsed(List.of(
				new ParsedMovement(0, MovementDirection.IN, "41", "N", null, null, null, null, null, null, null, null, null, SourceLocator.empty())),
				List.of())).when(parser).parse(any());
		when(publications.existsOverlappingPublishedSales(any(), any())).thenReturn(true);
		stubPublishHappy();
		ImportedFileResult result = service.ingestIntoJob(jobId, new ByteArrayInputStream(new byte[] {1}), "in.qrp");
		assertTrue(result.published());
		assertEquals("VALID", result.parseStatus());
	}

	private void stubNewArtifactIngest() {
		stubNewArtifactIngestWithoutOpen();
		when(storage.openVerified(anyString(), anyString(), anyLong())).thenReturn(new ByteArrayInputStream(new byte[] {1}));
	}

	private void stubNewArtifactIngestWithoutOpen() {
		when(storage.putIfAbsent(any(), any())).thenAnswer(this::storedFromDescriptor);
		when(rawArtifacts.findBySha256(anyString())).thenReturn(Optional.empty());
		when(rawArtifacts.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(attempts.findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(any(), any(), any()))
				.thenReturn(Optional.empty());
		when(attempts.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(files.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(files.findById(any())).thenAnswer(inv -> Optional.of(new ImportFileEntity(
				inv.getArgument(0), UUID.randomUUID(), UUID.randomUUID(), "f.qrp", "INTERPDV", "PROCESSING")));
		when(attempts.findById(any())).thenAnswer(inv -> Optional.of(new ParseAttemptEntity(
				inv.getArgument(0), UUID.randomUUID(), InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, "PROCESSING", 1)));
	}

	private void stubPublishHappy() {
		when(products.findByExternalSourceAndExternalId(any(), any())).thenReturn(Optional.empty());
		when(products.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(sales.findByExternalSourceAndExternalSaleId(any(), any())).thenReturn(Optional.empty());
		when(sales.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(publications.findByRawArtifactId(any())).thenReturn(Optional.empty());
		when(publications.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	private StoredRawFile storedFromDescriptor(org.mockito.invocation.InvocationOnMock inv) {
		RawFileDescriptor d = inv.getArgument(1);
		return new StoredRawFile("00/00/" + d.sha256(), d.sha256(), d.byteSize(), true);
	}

	@SuppressWarnings("unchecked")
	private static void stubTx(TransactionTemplate tx) {
		when(tx.execute(any())).thenAnswer(inv -> {
			TransactionCallback<Object> cb = inv.getArgument(0);
			return cb.doInTransaction(mock(TransactionStatus.class));
		});
		doAnswer(inv -> {
			Consumer<TransactionStatus> action = inv.getArgument(0);
			action.accept(mock(TransactionStatus.class));
			return null;
		}).when(tx).executeWithoutResult(any());
	}

	private static HttpStatus status(Runnable action) {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, action::run);
		return HttpStatus.valueOf(ex.getStatusCode().value());
	}

	private static ImportFileEntity file(UUID jobId, String status) {
		return new ImportFileEntity(UUID.randomUUID(), jobId, UUID.randomUUID(), "f.qrp", "INTERPDV", status);
	}

	private static ParseAttemptEntity attempt(UUID artifactId, String status, int count) {
		return new ParseAttemptEntity(UUID.randomUUID(), artifactId, InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION, status, count);
	}

	private static ParsedImport validParsed(List<ParsedMovement> moves, List<ParseIssue> issues) {
		return new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				"41", "NAME", moves,
				new ParsedImportTotals(null, BigDecimal.ONE, null, BigDecimal.TEN, null, null),
				ParsedImportStats.empty(), issues);
	}

	private static ParsedMovement outSale(String saleId) {
		return new ParsedMovement(
				0, MovementDirection.OUT, "41", "NAME", saleId, LocalDateTime.now(),
				BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
				null, null, null, SourceLocator.empty());
	}
}
