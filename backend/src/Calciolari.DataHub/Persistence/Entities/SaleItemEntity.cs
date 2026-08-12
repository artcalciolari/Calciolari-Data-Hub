namespace Calciolari.DataHub.Persistence.Entities;

public sealed class SaleItemEntity
{
    public Guid Id { get; set; }
    public Guid SaleId { get; set; }
    public Guid ProductId { get; set; }
    public Guid ParseAttemptId { get; set; }
    public int SourceRecordIndex { get; set; }
    public decimal Quantity { get; set; }
    public decimal UnitPrice { get; set; }
    public decimal? DiscountPercentage { get; set; }
    public decimal Total { get; set; }
    public decimal? PreviousStock { get; set; }
    public decimal? ResultingStock { get; set; }

    public SaleItemEntity()
    {
    }

    public SaleItemEntity(
        Guid id,
        Guid saleId,
        Guid productId,
        Guid parseAttemptId,
        int sourceRecordIndex,
        decimal quantity,
        decimal unitPrice,
        decimal? discountPercentage,
        decimal total,
        decimal? previousStock,
        decimal? resultingStock)
    {
        Id = id;
        SaleId = saleId;
        ProductId = productId;
        ParseAttemptId = parseAttemptId;
        SourceRecordIndex = sourceRecordIndex;
        Quantity = quantity;
        UnitPrice = unitPrice;
        DiscountPercentage = discountPercentage;
        Total = total;
        PreviousStock = previousStock;
        ResultingStock = resultingStock;
    }
}
