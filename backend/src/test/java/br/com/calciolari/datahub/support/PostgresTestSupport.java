package br.com.calciolari.datahub.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.DynamicPropertyRegistry;

import javax.sql.DataSource;

/**
 * Shared datasource wiring for integration tests. Prefers a local PostgreSQL
 * (always available in the Cloud agent VM). Set {@code datahub.test.jdbc-url}
 * to override.
 *
 * <p>Uses one shared raw-storage temp root and a small Hikari pool so multiple
 * {@code @SpringBootTest} contexts do not exhaust Postgres connections. Call
 * {@link #cleanRawStorage()} from {@code @BeforeEach} alongside DB truncate.
 */
public final class PostgresTestSupport {

	private static final Path SHARED_RAW_ROOT = createSharedRawRoot();

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
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
		registry.add("spring.datasource.hikari.idle-timeout", () -> "3000");
		registry.add("datahub.raw-storage.root", SHARED_RAW_ROOT::toString);
	}

	public static Path rawStorageRoot() {
		return SHARED_RAW_ROOT;
	}

	public static void cleanRawStorage() {
		if (!Files.isDirectory(SHARED_RAW_ROOT)) {
			return;
		}
		try (var walk = Files.walk(SHARED_RAW_ROOT)) {
			walk.sorted(Comparator.reverseOrder())
					.filter(p -> !p.equals(SHARED_RAW_ROOT))
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						}
						catch (IOException ignored) {
						}
					});
		}
		catch (IOException ignored) {
		}
	}

	public static void cleanDatabase(JdbcTemplate jdbcTemplate) {
		DataSource dataSource = jdbcTemplate.getDataSource();
		if (dataSource == null) {
			throw new IllegalStateException("JdbcTemplate has no DataSource");
		}
		var connection = DataSourceUtils.getConnection(dataSource);
		try {
			try (var statement = connection.createStatement()) {
				statement.execute("SELECT pg_advisory_lock(42424242)");
				statement.execute("SET LOCAL lock_timeout TO '10s'");
				statement.execute("""
						TRUNCATE TABLE
						  sale_item, sale, product, validation_result, parsed_movement,
						  artifact_publication, import_file, parse_attempt, import_job, raw_artifact
						RESTART IDENTITY CASCADE
						""");
			}
		}
		catch (java.sql.SQLException ex) {
			throw new org.springframework.jdbc.UncategorizedSQLException("cleanDatabase failed", null, ex);
		}
		finally {
			try (var unlock = connection.createStatement()) {
				unlock.execute("SELECT pg_advisory_unlock(42424242)");
			}
			catch (java.sql.SQLException ignored) {
			}
			DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}

	private static Path createSharedRawRoot() {
		try {
			return Files.createTempDirectory("datahub-raw-it-shared").toAbsolutePath().normalize();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
