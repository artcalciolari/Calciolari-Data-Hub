package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.imports.application.ImportIngestionService.ReprocessResult;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactRepository;
import br.com.calciolari.datahub.imports.infrastructure.storage.LocalRawFileStorage;
import br.com.calciolari.datahub.imports.support.FixturePackage;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemRepository;
import br.com.calciolari.datahub.support.PostgresTestSupport;

@SpringBootTest
class ImportReprocessIntegrationTest {

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		PostgresTestSupport.registerDataSource(registry);
	}

	@Autowired
	ImportIngestionService ingestionService;
	@Autowired
	RawArtifactRepository rawArtifactRepository;
	@Autowired
	ArtifactPublicationRepository artifactPublicationRepository;
	@Autowired
	ParseAttemptRepository parseAttemptRepository;
	@Autowired
	SaleItemRepository saleItemRepository;
	@Autowired
	LocalRawFileStorage rawFileStorage;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() throws Exception {
		PostgresTestSupport.cleanDatabase(jdbcTemplate);
		PostgresTestSupport.cleanRawStorage();
		Path root = rawFileStorage.root();
		if (Files.isDirectory(root)) {
			try (var walk = Files.walk(root)) {
				walk.sorted(java.util.Comparator.reverseOrder())
						.filter(p -> !p.equals(root))
						.forEach(p -> {
							try {
								Files.deleteIfExists(p);
							}
							catch (Exception ignored) {
							}
						});
			}
		}
	}

	@Test
	void successfulReprocessSwapsActivePointer() {
		byte[] bytes = FixturePackage.requireBytes("fixture-b");
		ImportedFileResult first = ingestionService.ingest(
				new ByteArrayInputStream(bytes), "AUDITORIA 41, 01_07-20_07.QRP");
		UUID previousActive = first.parseAttemptId();
		assertEquals(1, parseAttemptRepository.count());
		assertEquals(134, saleItemRepository.countByParseAttemptId(previousActive));

		ReprocessResult reprocessed = ingestionService.reprocess(first.importFileId());
		assertTrue(reprocessed.published());
		assertEquals("VALID", reprocessed.parseStatus());
		assertEquals(previousActive, reprocessed.previousActiveParseAttemptId());
		assertNotEquals(previousActive, reprocessed.parseAttemptId());
		assertEquals(2, parseAttemptRepository.count());

		ArtifactPublicationEntity publication = artifactPublicationRepository
				.findByRawArtifactId(first.rawArtifactId())
				.orElseThrow();
		assertEquals(reprocessed.parseAttemptId(), publication.getActiveParseAttemptId());
		assertEquals(134, saleItemRepository.countByParseAttemptId(reprocessed.parseAttemptId()));
		assertEquals(268, saleItemRepository.count());
	}

	@Test
	void failedReprocessPreservesActivePointer() throws Exception {
		byte[] bytes = FixturePackage.requireBytes("fixture-b");
		ImportedFileResult first = ingestionService.ingest(
				new ByteArrayInputStream(bytes), "AUDITORIA 41, 01_07-20_07.QRP");
		UUID previousActive = artifactPublicationRepository
				.findByRawArtifactId(first.rawArtifactId())
				.orElseThrow()
				.getActiveParseAttemptId();

		RawArtifactEntity artifact = rawArtifactRepository.findById(first.rawArtifactId()).orElseThrow();
		Path path = rawFileStorage.root().resolve(artifact.getStorageKey());
		byte[] junk = "NOT_A_VALID_QRP".getBytes();
		Files.write(path, junk);
		String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(junk));
		jdbcTemplate.update(
				"UPDATE raw_artifact SET sha256 = ?, byte_size = ? WHERE id = ?",
				sha, junk.length, artifact.getId());

		ReprocessResult failed = ingestionService.reprocess(first.importFileId());
		assertFalse(failed.published());
		assertEquals("FAILED", failed.parseStatus());
		assertEquals(previousActive, failed.previousActiveParseAttemptId());

		ArtifactPublicationEntity publication = artifactPublicationRepository
				.findByRawArtifactId(first.rawArtifactId())
				.orElseThrow();
		assertEquals(previousActive, publication.getActiveParseAttemptId());
		assertEquals(2, parseAttemptRepository.count());
		assertEquals(134, saleItemRepository.countByParseAttemptId(previousActive));
	}

	@Test
	void corruptedRawDetectedBeforeNewAttempt() throws Exception {
		byte[] bytes = FixturePackage.requireBytes("fixture-a");
		ImportedFileResult first = ingestionService.ingest(
				new ByteArrayInputStream(bytes), "AUDITORIA.QRP");
		UUID previousActive = artifactPublicationRepository
				.findByRawArtifactId(first.rawArtifactId())
				.orElseThrow()
				.getActiveParseAttemptId();

		RawArtifactEntity artifact = rawArtifactRepository.findById(first.rawArtifactId()).orElseThrow();
		Path path = rawFileStorage.root().resolve(artifact.getStorageKey());
		Files.write(path, new byte[] {0x00, 0x01, 0x02});

		ResponseStatusException ex = assertThrows(
				ResponseStatusException.class,
				() -> ingestionService.reprocess(first.importFileId()));
		assertEquals(409, ex.getStatusCode().value());
		assertEquals(1, parseAttemptRepository.count());
		assertEquals(
				previousActive,
				artifactPublicationRepository.findByRawArtifactId(first.rawArtifactId()).orElseThrow()
						.getActiveParseAttemptId());
	}

	@Test
	void sequentialReprocessesAreSerialized() {
		byte[] bytes = FixturePackage.requireBytes("fixture-b");
		ImportedFileResult first = ingestionService.ingest(
				new ByteArrayInputStream(bytes), "AUDITORIA 41, 01_07-20_07.QRP");

		ReprocessResult firstDone = ingestionService.reprocess(first.importFileId());
		ReprocessResult secondDone = ingestionService.reprocess(first.importFileId());
		assertTrue(firstDone.published());
		assertTrue(secondDone.published());
		assertNotEquals(firstDone.parseAttemptId(), secondDone.parseAttemptId());
		assertEquals(3, parseAttemptRepository.count());

		UUID active = artifactPublicationRepository.findByRawArtifactId(first.rawArtifactId())
				.orElseThrow()
				.getActiveParseAttemptId();
		assertEquals(secondDone.parseAttemptId(), active);
		assertEquals(134, saleItemRepository.countByParseAttemptId(active));
	}
}
