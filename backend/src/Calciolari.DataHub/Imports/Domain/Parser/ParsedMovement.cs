namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Normalized source movement. Money and quantities are decimal only.
/// </summary>
public sealed record ParsedMovement(
    int SourceRecordIndex,
    MovementDirection Direction,
    string? ExternalProductId,
    string? ProductName,
    string? ExternalSaleId,
    DateTime? OccurredAt,
    decimal? Quantity,
    decimal? UnitPrice,
    decimal? DiscountPercentage,
    decimal? Total,
    decimal? PreviousStock,
    decimal? ResultingStock,
    string? Manufacturer,
    SourceLocator SourceLocator);
