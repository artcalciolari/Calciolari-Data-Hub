package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.ParserInput;
import br.com.calciolari.datahub.imports.support.FixturePackage;

class InterPdvQrpParserEdgeTest {

	@Test
	void supportsQrpByExtensionOrDetectedType() {
		InterPdvQrpParser parser = new InterPdvQrpParser();
		assertTrue(parser.supports(new ParserInput(
				InputStream.nullInputStream(), 0, "report.QRP", null)));
		assertTrue(parser.supports(new ParserInput(
				InputStream.nullInputStream(), 0, "x", "qrp")));
		assertFalse(parser.supports(new ParserInput(
				InputStream.nullInputStream(), 0, "readme.txt", null)));
		assertThrows(NullPointerException.class, () -> parser.supports(null));
		assertThrows(NullPointerException.class, () -> parser.parse(null));
	}

	@Test
	void rejectsInvalidMaxBytes() {
		assertThrows(IllegalArgumentException.class,
				() -> new InterPdvQrpParser(new QrpContainerReader(), new EmfTextRecordExtractor(),
						new InterPdvReportLayoutMapper(), new InterPdvParsedImportValidator(), 0));
	}

	@Test
	void emptyPayloadIsFatalNoEmfPages() {
		InterPdvQrpParser parser = new InterPdvQrpParser();
		var parsed = parser.parse(new ParserInput(new ByteArrayInputStream(new byte[0]), 0, "x.qrp", "QRP"));
		assertTrue(parsed.hasFatalOrError());
		assertTrue(parsed.issues().stream().anyMatch(i -> "NO_EMF_PAGES".equals(i.code())));
	}

	@Test
	void truncatedFixtureIsFatalOrEmptyText() {
		byte[] full = FixturePackage.requireBytes("fixture-a");
		byte[] truncated = new byte[Math.min(120, full.length)];
		System.arraycopy(full, 0, truncated, 0, truncated.length);
		InterPdvQrpParser parser = new InterPdvQrpParser();
		var parsed = parser.parse(new ParserInput(new ByteArrayInputStream(truncated), truncated.length, "t.qrp", "QRP"));
		assertTrue(parsed.issues().stream().anyMatch(i -> i.severity() == IssueSeverity.FATAL
				|| i.severity() == IssueSeverity.ERROR));
	}

	@Test
	void enforcesMaxBytes() {
		InterPdvQrpParser parser = new InterPdvQrpParser(new QrpContainerReader(), new EmfTextRecordExtractor(),
				new InterPdvReportLayoutMapper(), new InterPdvParsedImportValidator(), 4);
		byte[] five = new byte[] {1, 2, 3, 4, 5};
		assertThrows(IllegalArgumentException.class,
				() -> parser.parse(new ParserInput(new ByteArrayInputStream(five), 5, "x.qrp", "QRP")));
	}

	@Test
	void readFailureWrapsIOException() {
		InterPdvQrpParser parser = new InterPdvQrpParser();
		InputStream broken = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("boom");
			}
		};
		assertThrows(java.io.UncheckedIOException.class,
				() -> parser.parse(new ParserInput(broken, 1, "x.qrp", "QRP")));
	}
}
