using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Shared.Api;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Catalog.Application;

public sealed class ProductQueryService
{
    private readonly DataHubDbContext _db;

    public ProductQueryService(DataHubDbContext db)
    {
        _db = db;
    }

    public PageResponse<ProductSummary> List(string? query, int page, int size)
    {
        var q = BlankToNull(query);
        var published = PublishedProducts();
        if (q is not null)
        {
            var like = "%" + q + "%";
            published = published.Where(p =>
                EF.Functions.ILike(p.Name, like) || EF.Functions.ILike(p.ExternalId, like));
        }

        var ordered = published.OrderBy(p => p.Name);
        var total = ordered.Count();
        var content = ordered.Skip(page * size).Take(size)
            .Select(p => new ProductSummary(p.Id, p.ExternalSource, p.ExternalId, p.Name, null))
            .ToList();
        return PageResponse<ProductSummary>.Of(content, page, size, total);
    }

    public ProductDetail Get(Guid id)
    {
        var product = PublishedProducts().SingleOrDefault(p => p.Id == id)
                      ?? throw new ApiException(StatusCodes.Status404NotFound, "Product not found");
        return new ProductDetail(
            product.Id,
            product.ExternalSource,
            product.ExternalId,
            product.Name,
            null,
            product.FirstSeenParseAttemptId);
    }

    private IQueryable<Persistence.Entities.ProductEntity> PublishedProducts() =>
        _db.Products.AsNoTracking().Where(p =>
            _db.SaleItems.Any(si =>
                si.ProductId == p.Id
                && _db.ArtifactPublications.Any(ap => ap.ActiveParseAttemptId == si.ParseAttemptId)));

    internal static string? BlankToNull(string? value) =>
        string.IsNullOrWhiteSpace(value) ? null : value.Trim();
}

public sealed record ProductSummary(
    Guid Id,
    string ExternalSource,
    string ExternalId,
    string Name,
    string? Unit);

public sealed record ProductDetail(
    Guid Id,
    string ExternalSource,
    string ExternalId,
    string Name,
    string? Unit,
    Guid FirstSeenParseAttemptId);
