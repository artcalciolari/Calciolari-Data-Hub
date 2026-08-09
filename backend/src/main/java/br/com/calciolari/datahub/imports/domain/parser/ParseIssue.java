package br.com.calciolari.datahub.imports.domain.parser;

import java.util.Objects;

/**
 * Structured parser/validation issue. Messages must be sanitized; never embed raw
 * binary snippets.
 */
public record ParseIssue(
		String code,
		IssueSeverity severity,
		IssueStage stage,
		SourceLocator sourceLocator,
		String message
) {
	public ParseIssue {
		Objects.requireNonNull(code, "code");
		Objects.requireNonNull(severity, "severity");
		Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(message, "message");
		if (sourceLocator == null) {
			sourceLocator = SourceLocator.empty();
		}
	}
}
