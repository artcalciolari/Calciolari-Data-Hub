package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductRepository;
import br.com.calciolari.datahub.imports.domain.hints.FilenameHintsParser;
import br.com.calciolari.datahub.imports.domain.parser.ImportParser;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ArtifactPublicationRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParsedMovementRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ValidationResultRepository;
import br.com.calciolari.datahub.imports.infrastructure.storage.RawFileStorage;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemRepository;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleRepository;
import tools.jackson.databind.ObjectMapper;

/**
 * Spool-and-hash failures, which abort the upload before any repository or
 * raw-storage interaction happens.
 */
class ImportIngestionServiceSpoolFailureTest {

	private ImportIngestionService service;

	@BeforeEach
	void setUp() {
		service = new ImportIngestionService(
				mock(RawFileStorage.class),
				mock(ImportParser.class),
				mock(FilenameHintsParser.class),
				mock(ObjectMapper.class),
				mock(TransactionTemplate.class),
				mock(RawArtifactRepository.class),
				mock(ImportJobRepository.class),
				mock(ImportFileRepository.class),
				mock(ParseAttemptRepository.class),
				mock(ArtifactPublicationRepository.class),
				mock(ParsedMovementRepository.class),
				mock(ValidationResultRepository.class),
				mock(ProductRepository.class),
				mock(SaleRepository.class),
				mock(SaleItemRepository.class));
	}

	@Test
	void unreadableUploadStreamBecomesUncheckedIo() {
		InputStream broken = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("upload stream broke");
			}

			@Override
			public int read(byte[] buffer, int offset, int length) throws IOException {
				throw new IOException("upload stream broke");
			}
		};

		assertThrows(UncheckedIOException.class,
				() -> service.ingestIntoJob(UUID.randomUUID(), broken, "broken.qrp"));
	}

	@Test
	void missingSha256DigestFailsFast() {
		try (MockedStatic<MessageDigest> digests = mockStatic(MessageDigest.class)) {
			digests.when(() -> MessageDigest.getInstance("SHA-256"))
					.thenThrow(new NoSuchAlgorithmException("SHA-256"));
			assertThrows(IllegalStateException.class,
					() -> service.ingestIntoJob(
							UUID.randomUUID(), new ByteArrayInputStream(new byte[] {1}), "x.qrp"));
		}
	}
}
