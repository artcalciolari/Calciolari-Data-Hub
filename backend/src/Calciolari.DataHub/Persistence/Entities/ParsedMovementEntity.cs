using Calciolari.DataHub.Imports.Domain.Parser;

namespace Calciolari.DataHub.Persistence.Entities;

public sealed class ParsedMovementEntity
{
    public Guid Id { get; set; }
    public Guid ParseAttemptId { get; set; }
    public int SourceRecordIndex { get; set; }
    public string Direction { get; set; } = string.Empty;
    public string? ExternalProductId { get; set; }
    public string? ProductName { get; set; }
    public string? ExternalSaleId { get; set; }
    public DateTime? OccurredAt { get; set; }
    public decimal? Quantity { get; set; }
    public decimal? UnitPrice { get; set; }
    public decimal? DiscountPercentage { get; set; }
    public decimal? Total { get; set; }
    public decimal? PreviousStock { get; set; }
    public decimal? ResultingStock { get; set; }
    public string? Manufacturer { get; set; }
    public string? SourceLocator { get; set; }

    public static ParsedMovementEntity From(Guid id, Guid parseAttemptId, ParsedMovement movement) =>
        new()
        {
            Id = id,
            ParseAttemptId = parseAttemptId,
            SourceRecordIndex = movement.SourceRecordIndex,
            Direction = movement.Direction.WireName(),
            ExternalProductId = movement.ExternalProductId,
            ProductName = movement.ProductName,
            ExternalSaleId = movement.ExternalSaleId,
            OccurredAt = movement.OccurredAt,
            Quantity = movement.Quantity,
            UnitPrice = movement.UnitPrice,
            DiscountPercentage = movement.DiscountPercentage,
            Total = movement.Total,
            PreviousStock = movement.PreviousStock,
            ResultingStock = movement.ResultingStock,
            Manufacturer = movement.Manufacturer,
            SourceLocator = movement.SourceLocator.ToString()
        };
}
