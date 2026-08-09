package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import br.com.calciolari.datahub.imports.domain.parser.ImportParser;
import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.IssueStage;
import br.com.calciolari.datahub.imports.domain.parser.ParseIssue;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportStats;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportTotals;
import br.com.calciolari.datahub.imports.domain.parser.ParserInput;
import br.com.calciolari.datahub.imports.domain.parser.SourceLocator;

/**
 * InterPDV QuickReport (.QRP) adapter. Binary layout ported from
 * {@code docs/poc/index.html} — not invented.
 */
public final class InterPdvQrpParser implements ImportParser {

	public static final String PARSER_NAME = InterPdvReportLayoutMapper.PARSER_NAME;
	public static final String PARSER_VERSION = InterPdvReportLayoutMapper.PARSER_VERSION;

	private final QrpContainerReader containerReader;
	private final EmfTextRecordExtractor textExtractor;
	private final InterPdvReportLayoutMapper layoutMapper;
	private final InterPdvParsedImportValidator validator;
	private final long maxBytes;

	public InterPdvQrpParser() {
		this(new QrpContainerReader(), new EmfTextRecordExtractor(), new InterPdvReportLayoutMapper(),
				new InterPdvParsedImportValidator(), 32L * 1024 * 1024);
	}

	public InterPdvQrpParser(
			QrpContainerReader containerReader,
			EmfTextRecordExtractor textExtractor,
			InterPdvReportLayoutMapper layoutMapper,
			InterPdvParsedImportValidator validator,
			long maxBytes) {
		this.containerReader = Objects.requireNonNull(containerReader);
		this.textExtractor = Objects.requireNonNull(textExtractor);
		this.layoutMapper = Objects.requireNonNull(layoutMapper);
		this.validator = Objects.requireNonNull(validator);
		if (maxBytes < 1) {
			throw new IllegalArgumentException("maxBytes must be >= 1");
		}
		this.maxBytes = maxBytes;
	}

	@Override
	public boolean supports(ParserInput input) {
		Objects.requireNonNull(input, "input");
		// Content sniffing happens in parse(); filename is never authoritative.
		// Provisional: accept when caller already classified as QRP or length looks plausible.
		if (input.detectedType() != null && input.detectedType().equalsIgnoreCase("QRP")) {
			return true;
		}
		String name = input.originalFilename();
		return name != null && name.toLowerCase().endsWith(".qrp");
	}

	@Override
	public ParsedImport parse(ParserInput input) {
		Objects.requireNonNull(input, "input");
		byte[] bytes = readLimited(input.content(), input.contentLength());
		List<EmfPage> pages = containerReader.findEmfPages(bytes);
		if (pages.isEmpty()) {
			return fatal("NO_EMF_PAGES", IssueStage.CONTAINER, "Nenhuma página EMF reconhecida neste QRP.");
		}

		List<EmfTextRun> texts = new ArrayList<>();
		for (int i = 0; i < pages.size(); i++) {
			texts.addAll(textExtractor.extract(bytes, pages.get(i), i));
		}
		if (texts.isEmpty()) {
			return fatal("NO_EMF_TEXT", IssueStage.EMF, "QRP reconhecido sem registros de texto EMF.");
		}

		ParsedImport mapped = layoutMapper.map(texts, pages.size());
		List<ParseIssue> merged = new ArrayList<>(mapped.issues());
		merged.addAll(validator.validate(mapped));
		return new ParsedImport(
				mapped.source(),
				mapped.parserName(),
				mapped.parserVersion(),
				mapped.externalProductId(),
				mapped.productName(),
				mapped.movements(),
				mapped.totals(),
				mapped.stats(),
				merged);
	}

	private byte[] readLimited(InputStream content, long contentLength) {
		try {
			if (contentLength > maxBytes) {
				throw new IllegalArgumentException("contentLength exceeds maxBytes (" + maxBytes + ")");
			}
			byte[] bytes = content.readAllBytes();
			if (bytes.length > maxBytes) {
				throw new IllegalArgumentException("payload exceeds maxBytes (" + maxBytes + ")");
			}
			if (contentLength > 0 && contentLength != bytes.length) {
				// Prefer actual bytes; length mismatch is non-fatal for parse itself.
			}
			return bytes;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("failed to read parser input", ex);
		}
	}

	private static ParsedImport fatal(String code, IssueStage stage, String message) {
		return new ParsedImport(
				InterPdvReportLayoutMapper.SOURCE,
				PARSER_NAME,
				PARSER_VERSION,
				null,
				null,
				List.of(),
				ParsedImportTotals.empty(),
				ParsedImportStats.empty(),
				List.of(new ParseIssue(code, IssueSeverity.FATAL, stage, SourceLocator.empty(), message)));
	}
}
