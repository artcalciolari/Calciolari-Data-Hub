namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Pure parse result. Must not depend on DB, network, clock, or default locale.
/// FilenameHints are produced outside the content parser.
/// </summary>
public sealed record ParsedImport(
    string Source,
    string ParserName,
    string ParserVersion,
    string? ExternalProductId,
    string? ProductName,
    IReadOnlyList<ParsedMovement> Movements,
    ParsedImportTotals Totals,
    ParsedImportStats Stats,
    IReadOnlyList<ParseIssue> Issues)
{
    public bool HasFatalOrError() =>
        Issues.Any(issue => issue.Severity is IssueSeverity.Fatal or IssueSeverity.Error);
}
