package br.com.calciolari.datahub.imports.domain.parser;

import java.util.List;
import java.util.Objects;

/**
 * Pure parse result. Must not depend on DB, network, clock, or default locale.
 * {@code FilenameHints} are produced outside the content parser.
 */
public record ParsedImport(
		String source,
		String parserName,
		String parserVersion,
		String externalProductId,
		String productName,
		List<ParsedMovement> movements,
		ParsedImportTotals totals,
		ParsedImportStats stats,
		List<ParseIssue> issues
) {
	public ParsedImport {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(parserName, "parserName");
		Objects.requireNonNull(parserVersion, "parserVersion");
		movements = List.copyOf(Objects.requireNonNull(movements, "movements"));
		totals = totals == null ? ParsedImportTotals.empty() : totals;
		stats = stats == null ? ParsedImportStats.empty() : stats;
		issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
	}

	public boolean hasFatalOrError() {
		return issues.stream().anyMatch(issue ->
				issue.severity() == IssueSeverity.FATAL || issue.severity() == IssueSeverity.ERROR);
	}
}
