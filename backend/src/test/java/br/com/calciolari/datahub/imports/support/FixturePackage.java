package br.com.calciolari.datahub.imports.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves fixture binaries and the versioned manifest. Fails with a clear
 * message when a required fixture package/file is absent.
 */
public final class FixturePackage {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private FixturePackage() {
	}

	public static JsonNode manifest() {
		try (InputStream in = FixturePackage.class.getResourceAsStream("/fixtures/manifest.json")) {
			if (in == null) {
				throw new IllegalStateException("classpath:/fixtures/manifest.json is missing");
			}
			return MAPPER.readTree(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static JsonNode requireFixture(String id) {
		JsonNode fixtures = manifest().path("fixtures");
		for (JsonNode fixture : fixtures) {
			if (id.equals(fixture.path("id").asText())) {
				return fixture;
			}
		}
		throw new IllegalArgumentException("Unknown fixture id: " + id);
	}

	public static byte[] requireBytes(String fixtureId) {
		JsonNode fixture = requireFixture(fixtureId);
		String status = fixture.path("status").asText("ABSENT");
		String relativePath = fixture.path("relativePath").asText(null);
		if (!"PRESENT".equals(status) || relativePath == null) {
			throw new IllegalStateException(
					"Fixture '" + fixtureId + "' is not present. "
							+ "See docs/fase-0-status.md and fixtures/README.md. "
							+ fixture.path("packageInstructions").asText(manifest().path("packageInstructions").asText("")));
		}

		Optional<Path> external = resolveExternal(relativePath);
		if (external.isPresent()) {
			return readAndVerify(external.get(), fixture);
		}

		String classpath = "/fixtures/" + relativePath;
		try (InputStream in = FixturePackage.class.getResourceAsStream(classpath)) {
			if (in == null) {
				throw new IllegalStateException(
						"Fixture bytes missing at classpath:" + classpath
								+ " (and DATAHUB_FIXTURES_DIR did not resolve it).");
			}
			byte[] bytes = in.readAllBytes();
			verifyHash(bytes, fixture);
			return bytes;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static boolean isPresent(String fixtureId) {
		JsonNode fixture = requireFixture(fixtureId);
		if (!"PRESENT".equals(fixture.path("status").asText())) {
			return false;
		}
		String relativePath = fixture.path("relativePath").asText(null);
		if (relativePath == null) {
			return false;
		}
		if (resolveExternal(relativePath).isPresent()) {
			return true;
		}
		return FixturePackage.class.getResource("/fixtures/" + relativePath) != null;
	}

	private static Optional<Path> resolveExternal(String relativePath) {
		String root = System.getenv("DATAHUB_FIXTURES_DIR");
		if (root == null || root.isBlank()) {
			return Optional.empty();
		}
		Path base = Path.of(root);
		Path candidate = base.resolve(relativePath);
		if (Files.isRegularFile(candidate)) {
			return Optional.of(candidate);
		}
		Path flat = base.resolve(Path.of(relativePath).getFileName());
		if (Files.isRegularFile(flat)) {
			return Optional.of(flat);
		}
		return Optional.empty();
	}

	private static byte[] readAndVerify(Path path, JsonNode fixture) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			verifyHash(bytes, fixture);
			return bytes;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void verifyHash(byte[] bytes, JsonNode fixture) {
		String expectedSha = fixture.path("sha256").asText(null);
		long expectedSize = fixture.path("byteSize").asLong(-1);
		if (expectedSize >= 0 && bytes.length != expectedSize) {
			throw new IllegalStateException(
					"Fixture size mismatch for " + fixture.path("id").asText()
							+ ": expected " + expectedSize + " got " + bytes.length);
		}
		if (expectedSha != null && !expectedSha.isBlank()) {
			String actual = sha256(bytes);
			if (!expectedSha.equalsIgnoreCase(actual)) {
				throw new IllegalStateException(
						"Fixture SHA-256 mismatch for " + fixture.path("id").asText()
								+ ": expected " + expectedSha + " got " + actual);
			}
		}
	}

	private static String sha256(byte[] bytes) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(Objects.requireNonNull(bytes)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
