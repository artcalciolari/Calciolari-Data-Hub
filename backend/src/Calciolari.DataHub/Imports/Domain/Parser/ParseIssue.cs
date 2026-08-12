namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Structured parser/validation issue. Messages must be sanitized; never embed raw
/// binary snippets.
/// </summary>
public sealed record ParseIssue(
    string Code,
    IssueSeverity Severity,
    IssueStage Stage,
    SourceLocator SourceLocator,
    string Message)
{
    public static ParseIssue Create(
        string code,
        IssueSeverity severity,
        IssueStage stage,
        SourceLocator? sourceLocator,
        string message)
    {
        ArgumentNullException.ThrowIfNull(code);
        ArgumentNullException.ThrowIfNull(message);
        return new ParseIssue(code, severity, stage, sourceLocator ?? SourceLocator.Empty, message);
    }
}
