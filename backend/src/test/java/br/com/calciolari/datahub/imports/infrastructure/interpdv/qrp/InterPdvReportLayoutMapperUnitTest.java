package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.support.FixturePackage;

class InterPdvReportLayoutMapperUnitTest {

	@Test
	void rejectsInvalidTolerance() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> new InterPdvReportLayoutMapper(0));
	}

	@Test
	void missingProductIsFatal() {
		var mapped = new InterPdvReportLayoutMapper().map(
				List.of(new EmfTextRun(0, 0, 0, "random label")), 1);
		assertTrue(mapped.issues().stream()
				.anyMatch(i -> "PRODUCT_FIELD_MISSING".equals(i.code()) && i.severity() == IssueSeverity.FATAL));
	}

	@Test
	void mapsFixtureTextRuns() {
		byte[] bytes = FixturePackage.requireBytes("fixture-a");
		List<EmfPage> pages = new QrpContainerReader().findEmfPages(bytes);
		java.util.ArrayList<EmfTextRun> texts = new java.util.ArrayList<>();
		EmfTextRecordExtractor extractor = new EmfTextRecordExtractor();
		for (int i = 0; i < pages.size(); i++) {
			texts.addAll(extractor.extract(bytes, pages.get(i), i));
		}
		var mapped = new InterPdvReportLayoutMapper().map(texts, pages.size());
		assertTrue(mapped.externalProductId() != null);
		assertTrue(mapped.movements().size() > 0);
	}
}
