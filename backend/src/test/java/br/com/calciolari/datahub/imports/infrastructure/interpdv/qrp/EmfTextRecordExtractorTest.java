package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.support.FixturePackage;

class EmfTextRecordExtractorTest {

	@Test
	void rejectsInvalidLimits() {
		assertThrows(IllegalArgumentException.class, () -> new EmfTextRecordExtractor(0, 10));
		assertThrows(IllegalArgumentException.class, () -> new EmfTextRecordExtractor(10, 0));
	}

	@Test
	void extractsTextFromFixturePage() {
		byte[] bytes = FixturePackage.requireBytes("fixture-b");
		List<EmfPage> pages = new QrpContainerReader().findEmfPages(bytes);
		EmfTextRecordExtractor extractor = new EmfTextRecordExtractor();
		List<EmfTextRun> runs = extractor.extract(bytes, pages.get(0), 0);
		assertFalse(runs.isEmpty());
		assertTrue(runs.stream().anyMatch(r -> r.text().contains("Produto")));
	}

	@Test
	void rejectsPageBeyondBuffer() {
		byte[] bytes = new byte[10];
		EmfPage page = new EmfPage(0, 20);
		assertThrows(IllegalArgumentException.class,
				() -> new EmfTextRecordExtractor().extract(bytes, page, 0));
	}

	@Test
	void requiresNonNullArguments() {
		byte[] bytes = new byte[100];
		EmfPage page = new EmfPage(0, 50);
		assertThrows(NullPointerException.class, () -> new EmfTextRecordExtractor().extract(null, page, 0));
		assertThrows(NullPointerException.class, () -> new EmfTextRecordExtractor().extract(bytes, null, 0));
	}
}
