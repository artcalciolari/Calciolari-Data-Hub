namespace Calciolari.DataHub.Imports.Domain.Hints;

/// <summary>
/// Non-authoritative hints parsed from originalFilename.
/// Always INFERRED_DATA; never identity, period of record, or totals.
/// </summary>
public sealed record FilenameHints(
    string OriginalFilename,
    IncompleteDateRange? PeriodHint,
    IncompleteDate? SingleDateHint,
    string? ProductCodeHint = null)
{
    public static FilenameHints Empty(string? originalFilename) =>
        new(originalFilename ?? string.Empty, null, null, null);

    public bool IsEmpty =>
        PeriodHint is null && SingleDateHint is null && string.IsNullOrWhiteSpace(ProductCodeHint);
}
