using Calciolari.DataHub.Catalog.Application;
using Calciolari.DataHub.Shared.Api;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Calciolari.DataHub.Catalog.Api;

[ApiController]
[Route("api/products")]
[Authorize(Roles = "VIEWER,IMPORTER,ADMIN")]
public sealed class ProductController : ControllerBase
{
    private readonly ProductQueryService _productQueryService;

    public ProductController(ProductQueryService productQueryService)
    {
        _productQueryService = productQueryService;
    }

    [HttpGet]
    public PageResponse<ProductSummary> List([FromQuery] string? q, [FromQuery] int? page, [FromQuery] int? size) =>
        _productQueryService.List(q, PageParams.Page(page), PageParams.Size(size));

    [HttpGet("{id:guid}")]
    public ProductDetail Get(Guid id) => _productQueryService.Get(id);
}
