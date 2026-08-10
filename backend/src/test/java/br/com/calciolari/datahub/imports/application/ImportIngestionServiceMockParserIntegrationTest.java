package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import br.com.calciolari.datahub.imports.domain.parser.ImportParser;
import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.IssueStage;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;
import br.com.calciolari.datahub.imports.domain.parser.ParseIssue;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportStats;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportTotals;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.ParserInput;
import br.com.calciolari.datahub.imports.domain.parser.SourceLocator;
import br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp.InterPdvQrpParser;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobRepository;
import br.com.calciolari.datahub.imports.support.FixturePackage;
import br.com.calciolari.datahub.support.PostgresTestSupport;

@SpringBootTest
class ImportIngestionServiceMockParserIntegrationTest {

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		PostgresTestSupport.registerDataSource(registry);
	}

	@MockitoBean
	ImportParser importParser;

	@Autowired
	ImportIngestionService ingestionService;
	@MockitoSpyBean
	ArtifactPublicationRepository artifactPublicationRepository;
	@Autowired
	ImportJobRepository importJobRepository;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		PostgresTestSupport.cleanDatabase(jdbcTemplate);
		PostgresTestSupport.cleanRawStorage();
	}

	@Test
	void overlapValidationBlocksPublication() {
		when(importParser.parse(any(ParserInput.class))).thenReturn(validImport(List.of(
				movement("1001", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN))));
		doReturn(true).when(artifactPublicationRepository).existsOverlappingPublishedSales(any(), any());

		ImportedFileResult result = ingestionService.ingest(
				new ByteArrayInputStream(FixturePackage.requireBytes("fixture-a")), "mock.qrp");
		assertFalse(result.published());
		assertEquals("WARNING", result.parseStatus());
	}

	@Test
	void fatalParseMarksFailed() {
		when(importParser.parse(any(ParserInput.class))).thenReturn(fatalImport());
		ImportedFileResult result = ingestionService.ingest(
				new ByteArrayInputStream(new byte[] {1, 2, 3}), "bad.qrp");
		assertEquals("FAILED", result.parseStatus());
		assertEquals("FAILED", result.fileStatus());
	}

	@Test
	void errorParseMarksInvalid() {
		when(importParser.parse(any(ParserInput.class))).thenReturn(new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				null, null, List.of(), ParsedImportTotals.empty(), ParsedImportStats.empty(),
				List.of(new ParseIssue("X", IssueSeverity.ERROR, IssueStage.VALIDATION, SourceLocator.empty(), "err"))));
		ImportedFileResult result = ingestionService.ingest(
				new ByteArrayInputStream(new byte[] {1, 2, 3}), "bad.qrp");
		assertEquals("INVALID", result.parseStatus());
	}

	@Test
	void reprocessOverlapYieldsWarningWithoutSwappingPublication() {
		when(importParser.parse(any(ParserInput.class))).thenReturn(validImport(List.of(
				movement("1001", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN))));
		ImportedFileResult ingested = ingestionService.ingest(
				new ByteArrayInputStream(FixturePackage.requireBytes("fixture-a")), "mock.qrp");
		doReturn(true).when(artifactPublicationRepository).existsOverlappingPublishedSales(any(), any());
		var reprocessed = ingestionService.reprocess(ingested.importFileId());
		assertEquals("WARNING", reprocessed.parseStatus());
		assertFalse(reprocessed.published());
	}

	@Test
	void reprocessFatalMarksAttemptFailed() {
		when(importParser.parse(any(ParserInput.class)))
				.thenReturn(validImport(List.of(movement("1", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))))
				.thenReturn(fatalImport());
		ImportedFileResult ingested = ingestionService.ingest(
				new ByteArrayInputStream(new byte[] {1, 2}), "a.qrp");
		var failed = ingestionService.reprocess(ingested.importFileId());
		assertEquals("FAILED", failed.parseStatus());
		assertFalse(failed.published());
	}

	@Test
	void reprocessRuntimeFailureMarksAttemptFailed() {
		when(importParser.parse(any(ParserInput.class)))
				.thenReturn(validImport(List.of(movement("1", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))))
				.thenThrow(new RuntimeException("parse blew up"));
		ImportedFileResult ingested = ingestionService.ingest(
				new ByteArrayInputStream(new byte[] {1, 2}), "a.qrp");
		assertThrows(RuntimeException.class, () -> ingestionService.reprocess(ingested.importFileId()));
	}

	@Test
	void warningIssuesYieldWarningParseStatus() {
		when(importParser.parse(any(ParserInput.class))).thenReturn(new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				"41", "NAME",
				List.of(movement("9", BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN)),
				new ParsedImportTotals(null, BigDecimal.ONE, null, BigDecimal.TEN, null, null),
				ParsedImportStats.empty(),
				List.of(new ParseIssue("W", IssueSeverity.WARNING, IssueStage.VALIDATION, SourceLocator.empty(), "warn"))));
		ImportedFileResult result = ingestionService.ingest(new ByteArrayInputStream(new byte[] {3}), "w.qrp");
		assertEquals("WARNING", result.parseStatus());
		assertTrue(result.published());
	}

	@Test
	void emptyJobCompletesAsFailed() {
		UUID jobId = ingestionService.createJob();
		ingestionService.completeJob(jobId);
		assertEquals("FAILED", importJobRepository.findById(jobId).orElseThrow().getStatus());
	}

	@Test
	void publishSkippedWithoutExternalProductId() {
		when(importParser.parse(any(ParserInput.class))).thenReturn(new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				null, null,
				List.of(movement("1002", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)),
				ParsedImportTotals.empty(), ParsedImportStats.empty(), List.of()));
		ImportedFileResult result = ingestionService.ingest(
				new ByteArrayInputStream(new byte[] {9}), "no-product.qrp");
		assertEquals(0, artifactPublicationRepository.count());
		assertEquals("VALID", result.parseStatus());
	}

	private static ParsedImport fatalImport() {
		return new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				null, null, List.of(), ParsedImportTotals.empty(), ParsedImportStats.empty(),
				List.of(new ParseIssue("X", IssueSeverity.FATAL, IssueStage.CONTAINER, SourceLocator.empty(), "fatal")));
	}

	private static ParsedImport validImport(List<ParsedMovement> movements) {
		return new ParsedImport(
				"INTERPDV", InterPdvQrpParser.PARSER_NAME, InterPdvQrpParser.PARSER_VERSION,
				"41", "NAME", movements,
				new ParsedImportTotals(null, BigDecimal.ONE, null, BigDecimal.TEN, null, null),
				ParsedImportStats.empty(), List.of());
	}

	private static ParsedMovement movement(String saleId, BigDecimal qty, BigDecimal price, BigDecimal total) {
		return new ParsedMovement(
				0, MovementDirection.OUT, "41", "NAME", saleId, LocalDateTime.now(),
				qty, price, BigDecimal.ZERO, total, null, null, null, SourceLocator.empty());
	}
}
