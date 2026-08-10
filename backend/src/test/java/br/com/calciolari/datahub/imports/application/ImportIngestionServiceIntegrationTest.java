package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactRepository;
import br.com.calciolari.datahub.imports.support.FixturePackage;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemRepository;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;
import br.com.calciolari.datahub.support.PostgresTestSupport;

@SpringBootTest
class ImportIngestionServiceIntegrationTest {

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		PostgresTestSupport.registerDataSource(registry);
	}

	@Autowired
	ImportIngestionService ingestionService;
	@Autowired
	RawArtifactRepository rawArtifactRepository;
	@Autowired
	ImportFileRepository importFileRepository;
	@Autowired
	ArtifactPublicationRepository artifactPublicationRepository;
	@Autowired
	ProductRepository productRepository;
	@Autowired
	SaleRepository saleRepository;
	@Autowired
	SaleItemRepository saleItemRepository;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		PostgresTestSupport.cleanDatabase(jdbcTemplate);
		PostgresTestSupport.cleanRawStorage();
	}

	@Test
	void fixtureBIngestPublishesAndDedupsByHash() {
		byte[] bytes = FixturePackage.requireBytes("fixture-b");

		ImportedFileResult first = ingestionService.ingest(
				new ByteArrayInputStream(bytes), "AUDITORIA 41, 01_07-20_07.QRP");
		assertTrue(first.published());
		assertFalse(first.deduplicated());
		assertEquals("IMPORTED", first.fileStatus());
		assertEquals("VALID", first.parseStatus());
		assertEquals(134, first.recordsFound());
		assertEquals(1, rawArtifactRepository.count());
		assertEquals(1, artifactPublicationRepository.count());
		assertTrue(productRepository.findByExternalSourceAndExternalId("INTERPDV", "41").isPresent());
		assertEquals(93, saleRepository.count());
		assertEquals(134, saleItemRepository.count());

		ImportedFileResult second = ingestionService.ingest(
				new ByteArrayInputStream(bytes), "copy-of-fixture-b.QRP");
		assertTrue(second.deduplicated());
		assertEquals(first.rawArtifactId(), second.rawArtifactId());
		assertEquals(first.parseAttemptId(), second.parseAttemptId());
		assertEquals(1, rawArtifactRepository.count());
		assertEquals(2, importFileRepository.count());
		assertEquals(134, saleItemRepository.count());
	}

	@Test
	void concurrentIdenticalUploadsShareOneArtifact() throws Exception {
		byte[] bytes = FixturePackage.requireBytes("fixture-a");
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			Callable<ImportedFileResult> task = () -> ingestionService.ingest(
					new ByteArrayInputStream(bytes), "AUDITORIA.QRP");
			@SuppressWarnings("unchecked")
			Future<ImportedFileResult>[] futures = new Future[4];
			for (int i = 0; i < futures.length; i++) {
				futures[i] = pool.submit(task);
			}
			UUID artifactId = null;
			int primaryPublished = 0;
			for (Future<ImportedFileResult> future : futures) {
				ImportedFileResult result = future.get();
				if (artifactId == null) {
					artifactId = result.rawArtifactId();
				}
				else {
					assertEquals(artifactId, result.rawArtifactId());
				}
				if (result.published() && !result.deduplicated()) {
					primaryPublished++;
				}
			}
			assertEquals(1, rawArtifactRepository.count());
			assertEquals(4, importFileRepository.count());
			assertTrue(artifactPublicationRepository.findByRawArtifactId(artifactId).isPresent());
			assertEquals(1, primaryPublished);
		}
		finally {
			pool.shutdownNow();
		}
	}
}
