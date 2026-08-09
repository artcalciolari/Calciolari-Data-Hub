package br.com.calciolari.datahub.imports.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductEntity;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.SourceLocator;
import br.com.calciolari.datahub.imports.infrastructure.storage.StoredRawFile;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleEntity;
import br.com.calciolari.datahub.sales.infrastructure.persistence.SaleItemEntity;

class PersistenceEntitiesTest {

	@Test
	void touchesImportEntities() throws Exception {
		UUID id = UUID.randomUUID();
		ImportJobEntity job = new ImportJobEntity(id, "PROCESSING");
		assertEquals(id, job.getId());
		assertEquals("PROCESSING", job.getStatus());
		job.setStatus("SUCCEEDED");
		job.setCompletedAt(Instant.now());
		assertNotNull(job.getCreatedAt());
		assertNotNull(job.getCompletedAt());
		invokeProtectedCtor(ImportJobEntity.class);

		RawArtifactEntity artifact = new RawArtifactEntity(id, "a".repeat(64), 10L, "00/00/" + "b".repeat(64), "QRP");
		assertEquals(id, artifact.getId());
		assertEquals("a".repeat(64), artifact.getSha256());
		assertEquals(10L, artifact.getByteSize());
		assertEquals("00/00/" + "b".repeat(64), artifact.getStorageKey());
		assertEquals("QRP", artifact.getDetectedType());
		assertNotNull(artifact.getCreatedAt());
		invokeProtectedCtor(RawArtifactEntity.class);

		ImportFileEntity file = new ImportFileEntity(id, id, id, "f.qrp", "INTERPDV", "PENDING");
		file.setParseAttemptId(id);
		file.setFilenameHints("{}");
		file.setStatus("IMPORTED");
		file.setDeduplicated(true);
		file.setDuplicateOfImportFileId(id);
		file.setCompletedAt(Instant.now());
		assertTrue(file.isDeduplicated());
		assertEquals("f.qrp", file.getOriginalFilename());
		invokeProtectedCtor(ImportFileEntity.class);

		ParseAttemptEntity attempt = new ParseAttemptEntity(id, id, "p", "v", "PROCESSING", 1);
		attempt.setStatus("VALID");
		attempt.setRecordsFound(3);
		attempt.setLeaseUntil(Instant.now());
		attempt.setLeaseOwner("x");
		attempt.setLeaseGeneration(2L);
		attempt.setStartedAt(Instant.now());
		attempt.setCompletedAt(Instant.now());
		attempt.setErrorSummary("err");
		assertEquals(id, attempt.getId());
		assertEquals(id, attempt.getRawArtifactId());
		assertEquals("p", attempt.getParserName());
		assertEquals("v", attempt.getParserVersion());
		assertEquals("VALID", attempt.getStatus());
		assertEquals(3, attempt.getRecordsFound());
		assertEquals(1, attempt.getAttemptCount());
		assertNotNull(attempt.getLeaseUntil());
		assertEquals("x", attempt.getLeaseOwner());
		assertEquals(2L, attempt.getLeaseGeneration());
		assertNotNull(attempt.getStartedAt());
		assertNotNull(attempt.getCompletedAt());
		assertEquals("err", attempt.getErrorSummary());
		invokeProtectedCtor(ParseAttemptEntity.class);

		ValidationResultEntity validation = new ValidationResultEntity(
				id, id, "CODE", "VALID", BigDecimal.ONE, BigDecimal.TWO,
				BigDecimal.ZERO, new BigDecimal("0.01"), "rv", "loc");
		assertEquals(id, validation.getId());
		assertEquals(id, validation.getParseAttemptId());
		assertEquals("CODE", validation.getCode());
		assertEquals("VALID", validation.getStatus());
		assertEquals(BigDecimal.ONE, validation.getSourceValue());
		assertEquals(BigDecimal.TWO, validation.getCalculatedValue());
		assertEquals(BigDecimal.ZERO, validation.getDifference());
		assertEquals(new BigDecimal("0.01"), validation.getTolerance());
		assertEquals("rv", validation.getRuleVersion());
		assertEquals("loc", validation.getSourceLocator());
		invokeProtectedCtor(ValidationResultEntity.class);

		ParsedMovement movement = new ParsedMovement(
				1, MovementDirection.OUT, "41", "NAME", "99", LocalDateTime.now(),
				BigDecimal.ONE, BigDecimal.TWO, BigDecimal.ZERO, BigDecimal.TWO,
				BigDecimal.TEN, BigDecimal.valueOf(9), "MFG", new SourceLocator(1, 2, 3L, "y=1"));
		ParsedMovementEntity pm = ParsedMovementEntity.from(id, id, movement);
		assertEquals(id, pm.getId());
		assertEquals(id, pm.getParseAttemptId());
		assertEquals(1, pm.getSourceRecordIndex());
		assertEquals("OUT", pm.getDirection());
		assertEquals("41", pm.getExternalProductId());
		assertEquals("NAME", pm.getProductName());
		assertEquals("99", pm.getExternalSaleId());
		assertNotNull(pm.getOccurredAt());
		assertEquals(BigDecimal.ONE, pm.getQuantity());
		assertEquals(BigDecimal.TWO, pm.getUnitPrice());
		assertEquals(BigDecimal.ZERO, pm.getDiscountPercentage());
		assertEquals(BigDecimal.TWO, pm.getTotal());
		ParsedMovement noLocator = org.mockito.Mockito.mock(ParsedMovement.class);
		org.mockito.Mockito.when(noLocator.direction()).thenReturn(MovementDirection.IN);
		org.mockito.Mockito.when(noLocator.sourceLocator()).thenReturn(null);
		ParsedMovementEntity pm2 = ParsedMovementEntity.from(id, id, noLocator);
		assertEquals("IN", pm2.getDirection());
		invokeProtectedCtor(ParsedMovementEntity.class);

		ArtifactPublicationEntity pub = new ArtifactPublicationEntity(id, id);
		pub.setActiveParseAttemptId(id);
		pub.setPublishedAt(Instant.now());
		assertEquals(id, pub.getRawArtifactId());
		assertEquals(id, pub.getActiveParseAttemptId());
		assertNotNull(pub.getPublishedAt());
		invokeProtectedCtor(ArtifactPublicationEntity.class);
	}

	@Test
	void touchesCatalogAndSalesEntities() throws Exception {
		UUID id = UUID.randomUUID();
		ProductEntity product = new ProductEntity(id, "INTERPDV", "41", "NAME", id);
		product.setName("NEW");
		assertEquals("NEW", product.getName());
		assertEquals("41", product.getExternalId());
		invokeProtectedCtor(ProductEntity.class);

		SaleEntity sale = new SaleEntity(id, "INTERPDV", "S1", LocalDateTime.now(), id);
		assertEquals(id, sale.getId());
		assertEquals("INTERPDV", sale.getExternalSource());
		assertEquals("S1", sale.getExternalSaleId());
		assertNotNull(sale.getOccurredAt());
		assertEquals(id, sale.getFirstSeenParseAttemptId());
		invokeProtectedCtor(SaleEntity.class);

		SaleItemEntity item = new SaleItemEntity(
				id, id, id, id, 0, BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE,
				BigDecimal.TEN, BigDecimal.valueOf(9));
		assertEquals(id, item.getId());
		assertEquals(id, item.getSaleId());
		assertEquals(id, item.getProductId());
		assertEquals(id, item.getParseAttemptId());
		assertEquals(0, item.getSourceRecordIndex());
		assertEquals(BigDecimal.ONE, item.getQuantity());
		assertEquals(BigDecimal.ONE, item.getUnitPrice());
		assertEquals(null, item.getDiscountPercentage());
		assertEquals(BigDecimal.ONE, item.getTotal());
		invokeProtectedCtor(SaleItemEntity.class);

		StoredRawFile stored = new StoredRawFile("00/00/" + "c".repeat(64), "c".repeat(64), 5L, true);
		assertTrue(stored.created());
		assertEquals(5L, stored.byteSize());
	}

	private static void invokeProtectedCtor(Class<?> type) throws Exception {
		Constructor<?> ctor = type.getDeclaredConstructor();
		ctor.setAccessible(true);
		assertNotNull(ctor.newInstance());
	}
}
