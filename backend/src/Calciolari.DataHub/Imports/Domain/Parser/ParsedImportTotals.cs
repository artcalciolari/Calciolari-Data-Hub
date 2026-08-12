namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Totals declared by the source report and/or derived strictly from parsed
/// records. Calculated fields are provenance CALCULATED_DATA.
/// </summary>
public sealed record ParsedImportTotals(
    decimal? SourceQuantityTotal,
    decimal? ParsedQuantityTotal,
    decimal? SourceRevenueTotal,
    decimal? ParsedRevenueTotal,
    DateTime? FirstMovementAt,
    DateTime? LastMovementAt)
{
    public static ParsedImportTotals Empty { get; } = new(null, null, null, null, null, null);
}
