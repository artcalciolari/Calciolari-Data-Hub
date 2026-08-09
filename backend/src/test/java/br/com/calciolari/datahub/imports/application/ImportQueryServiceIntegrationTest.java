package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import br.com.calciolari.datahub.imports.support.FixturePackage;
import br.com.calciolari.datahub.support.PostgresTestSupport;

@SpringBootTest
class ImportQueryServiceIntegrationTest {

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		PostgresTestSupport.registerDataSource(registry);
	}

	@Autowired
	ImportIngestionService ingestionService;
	@Autowired
	ImportQueryService queryService;
	@Autowired
	JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		PostgresTestSupport.cleanDatabase(jdbcTemplate);
		PostgresTestSupport.cleanRawStorage();
	}

	@Test
	void listsJobsAndReturnsFileDetail() {
		var ingested = ingestionService.ingest(
				new ByteArrayInputStream(FixturePackage.requireBytes("fixture-a")), "AUDITORIA.QRP");
		assertEquals(1, queryService.listJobs(0, 10).totalElements());
		assertEquals(ingested.jobId(), queryService.getJob(ingested.jobId()).id());
		assertEquals(ingested.importFileId(),
				queryService.getFile(ingested.jobId(), ingested.importFileId()).id());
	}
}
