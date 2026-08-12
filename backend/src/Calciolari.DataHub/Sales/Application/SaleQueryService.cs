using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Shared.Api;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Sales.Application;

public sealed class SaleQueryService
{
    private readonly DataHubDbContext _db;

    public SaleQueryService(DataHubDbContext db)
    {
        _db = db;
    }

    public PageResponse<SaleSummary> List(Guid? productId, DateTime? from, DateTime? to, int page, int size)
    {
        var fromBound = BoundFrom(from);
        var toBound = BoundTo(to);
        var query = PublishedSales()
            .Where(s => s.OccurredAt >= fromBound && s.OccurredAt <= toBound);
        if (productId is not null)
        {
            query = query.Where(s =>
                _db.SaleItems.Any(si =>
                    si.SaleId == s.Id
                    && si.ProductId == productId
                    && _db.ArtifactPublications.Any(ap => ap.ActiveParseAttemptId == si.ParseAttemptId)));
        }

        var ordered = query.OrderByDescending(s => s.OccurredAt).ThenByDescending(s => s.ExternalSaleId);
        var total = ordered.Count();
        var sales = ordered.Skip(page * size).Take(size).ToList();
        var content = sales.Select(sale => new SaleSummary(
            sale.Id,
            sale.ExternalSource,
            sale.ExternalSaleId,
            DateTimeText.Iso(sale.OccurredAt),
            DecimalText.ToPlainString(SumPublishedTotalForSale(sale.Id)))).ToList();
        return PageResponse<SaleSummary>.Of(content, page, size, total);
    }

    public SaleDetail Get(Guid id)
    {
        var sale = PublishedSales().SingleOrDefault(s => s.Id == id)
                   ?? throw new ApiException(StatusCodes.Status404NotFound, "Sale not found");
        var items = _db.SaleItems.AsNoTracking()
            .Where(si => si.SaleId == id
                         && _db.ArtifactPublications.Any(ap => ap.ActiveParseAttemptId == si.ParseAttemptId))
            .OrderBy(si => si.SourceRecordIndex)
            .ToList()
            .Select(ToItem)
            .ToList();
        return new SaleDetail(
            sale.Id,
            sale.ExternalSource,
            sale.ExternalSaleId,
            DateTimeText.Iso(sale.OccurredAt),
            items);
    }

    private SaleItemDto ToItem(Persistence.Entities.SaleItemEntity item)
    {
        var product = _db.Products.AsNoTracking().Single(p => p.Id == item.ProductId);
        return new SaleItemDto(
            item.Id,
            item.ProductId,
            product.Name,
            product.ExternalId,
            item.SourceRecordIndex,
            DecimalText.ToPlainString(item.Quantity),
            DecimalText.ToPlainString(item.UnitPrice),
            DecimalText.ToPlainString(item.DiscountPercentage),
            DecimalText.ToPlainString(item.Total));
    }

    private decimal SumPublishedTotalForSale(Guid saleId) =>
        _db.SaleItems.AsNoTracking()
            .Where(si => si.SaleId == saleId
                         && _db.ArtifactPublications.Any(ap => ap.ActiveParseAttemptId == si.ParseAttemptId))
            .Select(si => (decimal?)si.Total)
            .Sum() ?? 0m;

    private IQueryable<Persistence.Entities.SaleEntity> PublishedSales() =>
        _db.Sales.AsNoTracking().Where(s =>
            _db.SaleItems.Any(si =>
                si.SaleId == s.Id
                && _db.ArtifactPublications.Any(ap => ap.ActiveParseAttemptId == si.ParseAttemptId)));

    internal static DateTime BoundFrom(DateTime? from) =>
        AsUnspecified(from ?? new DateTime(1, 1, 1, 0, 0, 0, DateTimeKind.Unspecified));

    internal static DateTime BoundTo(DateTime? to) =>
        AsUnspecified(to ?? new DateTime(9999, 12, 31, 23, 59, 59, DateTimeKind.Unspecified));

    internal static DateTime AsUnspecified(DateTime value) =>
        DateTime.SpecifyKind(value, DateTimeKind.Unspecified);
}

public sealed record SaleSummary(
    Guid Id,
    string ExternalSource,
    string ExternalSaleId,
    string? OccurredAt,
    string? Total);

public sealed record SaleDetail(
    Guid Id,
    string ExternalSource,
    string ExternalSaleId,
    string? OccurredAt,
    IReadOnlyList<SaleItemDto> Items);

public sealed record SaleItemDto(
    Guid Id,
    Guid ProductId,
    string ProductName,
    string ProductExternalId,
    int SourceRecordIndex,
    string Quantity,
    string UnitPrice,
    string? DiscountPercentage,
    string Total);
