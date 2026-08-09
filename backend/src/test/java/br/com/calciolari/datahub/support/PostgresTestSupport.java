package br.com.calciolari.datahub.support;

import java.nio.file.Files;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Shared datasource wiring for integration tests. Prefers a local PostgreSQL
 * (always available in the Cloud agent VM). Set {@code datahub.test.jdbc-url}
 * to override. Testcontainers remains available for CI hosts with working Docker.
 */
public final class PostgresTestSupport {

	private PostgresTestSupport() {
	}

	public static void registerDataSource(DynamicPropertyRegistry registry) {
		String url = System.getenv().getOrDefault(
				"DATAHUB_TEST_JDBC_URL",
				System.getProperty(
						"datahub.test.jdbc-url",
						"jdbc:postgresql://127.0.0.1:5432/datahub"));
		String user = System.getenv().getOrDefault(
				"DATAHUB_TEST_JDBC_USER",
				System.getProperty("datahub.test.jdbc-user", "datahub"));
		String password = System.getenv().getOrDefault(
				"DATAHUB_TEST_JDBC_PASSWORD",
				System.getProperty("datahub.test.jdbc-password", "datahub"));
		registry.add("spring.datasource.url", () -> url);
		registry.add("spring.datasource.username", () -> user);
		registry.add("spring.datasource.password", () -> password);
		registry.add("datahub.raw-storage.root", () -> {
			try {
				return Files.createTempDirectory("datahub-raw-it").toAbsolutePath().toString();
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});
	}
}
