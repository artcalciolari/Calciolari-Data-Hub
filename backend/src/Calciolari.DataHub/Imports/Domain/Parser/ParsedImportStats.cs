namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Aggregate counts from a parse. Null means the metric was not observed / not
/// applicable yet — never invent zeros to look complete.
/// </summary>
public sealed record ParsedImportStats(
    int? Pages,
    int? Lines,
    int? UniqueSales,
    int? Entries,
    int? Exits)
{
    public static ParsedImportStats Empty { get; } = new(null, null, null, null, null);
}
