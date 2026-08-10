package br.com.calciolari.datahub.imports.domain.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

class DomainParserRecordsTest {

	@Test
	void parsedImportDefaultsAndFatalDetection() {
		ParsedImport emptyIssues = new ParsedImport(
				"S", "p", "v", null, null, List.of(), null, null, List.of());
		assertFalse(emptyIssues.hasFatalOrError());

		ParsedImport withError = new ParsedImport(
				"S", "p", "v", null, null, List.of(), null, null,
				List.of(new ParseIssue("X", IssueSeverity.ERROR, IssueStage.VALIDATION, SourceLocator.empty(), "m")));
		assertTrue(withError.hasFatalOrError());
	}

	@Test
	void parserInputRequiresNonNullStream() {
		org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
				() -> new ParserInput(null, 0, "f.qrp", "QRP"));
	}

	@Test
	void parsedMovementAndIssueDefaultNullLocator() {
		ParsedMovement movement = new ParsedMovement(
				0, MovementDirection.IN, null, null, null, null,
				null, null, null, null, null, null, null, null);
		assertTrue(movement.sourceLocator().page() == null);

		ParseIssue issue = new ParseIssue("C", IssueSeverity.INFO, IssueStage.VALIDATION, null, "msg");
		assertTrue(issue.sourceLocator().page() == null);
	}
}
