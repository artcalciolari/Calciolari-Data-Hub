package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ImportIngestionServiceSpoolTest {

	@Test
	void spoolAndHashIoAndMissingAlgorithm() throws Exception {
		InputStream broken = org.mockito.Mockito.mock(InputStream.class);
		when(broken.read(any(byte[].class))).thenThrow(new IOException("read"));
		assertThrows(UncheckedIOException.class,
				() -> ReflectionTestUtils.invokeMethod(ImportIngestionService.class, "spoolAndHash", broken));

		try (var digests = org.mockito.Mockito.mockStatic(java.security.MessageDigest.class)) {
			digests.when(() -> java.security.MessageDigest.getInstance("SHA-256"))
					.thenThrow(new java.security.NoSuchAlgorithmException("SHA-256"));
			assertThrows(IllegalStateException.class,
					() -> ReflectionTestUtils.invokeMethod(
							ImportIngestionService.class, "spoolAndHash", new ByteArrayInputStream(new byte[] {1})));
		}
	}
}
