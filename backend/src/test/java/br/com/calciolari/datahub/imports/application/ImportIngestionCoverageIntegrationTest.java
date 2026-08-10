package br.com.calciolari.datahub.imports.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Intentionally disabled. Coverage for ingest/reprocess branches lives in
 * {@link ImportIngestionServiceUnitTest} (Mockito). Previous Spring ITs under
 * this name raced on unique constraints and overloaded the shared Postgres.
 */
@Disabled("Superseded by ImportIngestionServiceUnitTest — do not reintroduce SpringBootTest coverage ITs here")
class ImportIngestionCoverageIntegrationTest {

	@Test
	void placeholder() {
		// no-op
	}
}
