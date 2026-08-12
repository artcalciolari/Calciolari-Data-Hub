using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Shared.Api;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Analytics.Application;

public sealed class DashboardQueryService
{
    private readonly DataHubDbContext _db;

    public DashboardQueryService(DataHubDbContext db)
    {
        _db = db;
    }

    public DashboardResponse Summarize(Guid? productId, DateTime? from, DateTime? to)
    {
        var fromBound = DateTime.SpecifyKind(
            from ?? new DateTime(1, 1, 1, 0, 0, 0, DateTimeKind.Unspecified), DateTimeKind.Unspecified);
        var toBound = DateTime.SpecifyKind(
            to ?? new DateTime(9999, 12, 31, 23, 59, 59, DateTimeKind.Unspecified), DateTimeKind.Unspecified);

        var items = PublishedItems(productId, fromBound, toBound).ToList();
        var revenue = items.Sum(i => i.Total);
        var quantity = items.Sum(i => i.Quantity);
        var salesCount = items.Select(i => i.SaleId).Distinct().Count();
        var itemCount = items.Count;
        var averageTicket = salesCount > 0
            ? decimal.Round(revenue / salesCount, 2, MidpointRounding.AwayFromZero).ToString(System.Globalization.CultureInfo.InvariantCulture)
            : null;

        var byDay = new SortedDictionary<DateOnly, (decimal Qty, decimal Tot)>();
        foreach (var row in items.Where(i => i.OccurredAt is not null))
        {
            var day = DateOnly.FromDateTime(row.OccurredAt!.Value);
            if (byDay.TryGetValue(day, out var acc))
            {
                byDay[day] = (acc.Qty + row.Quantity, acc.Tot + row.Total);
            }
            else
            {
                byDay[day] = (row.Quantity, row.Total);
            }
        }

        var daily = byDay.Select(entry => new DailyPoint(
            entry.Key.ToString("yyyy-MM-dd"),
            DecimalText.StripTrailingZerosToPlain(entry.Value.Qty),
            DecimalText.StripTrailingZerosToPlain(entry.Value.Tot))).ToList();

        var topProducts = items
            .GroupBy(i => i.ProductId)
            .Select(g => new
            {
                ProductId = g.Key,
                Quantity = g.Sum(x => x.Quantity),
                Revenue = g.Sum(x => x.Total)
            })
            .OrderByDescending(g => g.Revenue)
            .Take(5)
            .ToList()
            .Select(g =>
            {
                var product = _db.Products.AsNoTracking().Single(p => p.Id == g.ProductId);
                return new TopProduct(
                    product.Id,
                    product.Name,
                    product.ExternalId,
                    DecimalText.StripTrailingZerosToPlain(g.Quantity),
                    DecimalText.StripTrailingZerosToPlain(g.Revenue));
            })
            .ToList();

        DateTime? first = items.Where(i => i.OccurredAt is not null).Select(i => i.OccurredAt).Min();
        DateTime? last = items.Where(i => i.OccurredAt is not null).Select(i => i.OccurredAt).Max();

        return new DashboardResponse(
            DecimalText.StripTrailingZerosToPlain(revenue),
            DecimalText.StripTrailingZerosToPlain(quantity),
            salesCount,
            itemCount,
            averageTicket,
            DateTimeText.Iso(first),
            DateTimeText.Iso(last),
            daily,
            topProducts);
    }

    private IQueryable<ItemFact> PublishedItems(Guid? productId, DateTime fromBound, DateTime toBound)
    {
        var query =
            from si in _db.SaleItems.AsNoTracking()
            join s in _db.Sales.AsNoTracking() on si.SaleId equals s.Id
            join ap in _db.ArtifactPublications.AsNoTracking() on si.ParseAttemptId equals ap.ActiveParseAttemptId
            where s.OccurredAt >= fromBound && s.OccurredAt <= toBound
                  && (productId == null || si.ProductId == productId)
            select new ItemFact(si.SaleId, si.ProductId, si.Quantity, si.Total, s.OccurredAt);
        return query;
    }

    private sealed record ItemFact(Guid SaleId, Guid ProductId, decimal Quantity, decimal Total, DateTime? OccurredAt);
}

public sealed record DashboardResponse(
    string RevenueTotal,
    string QuantityTotal,
    long SalesCount,
    long ItemCount,
    string? AverageTicket,
    string? FirstMovementAt,
    string? LastMovementAt,
    IReadOnlyList<DailyPoint> Daily,
    IReadOnlyList<TopProduct> TopProducts);

public sealed record DailyPoint(string Date, string Quantity, string Revenue);

public sealed record TopProduct(
    Guid ProductId,
    string Name,
    string ExternalId,
    string Quantity,
    string Revenue);
