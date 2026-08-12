using Calciolari.DataHub.Sales.Application;
using Calciolari.DataHub.Shared.Api;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Calciolari.DataHub.Sales.Api;

[ApiController]
[Route("api/sales")]
[Authorize(Roles = "VIEWER,IMPORTER,ADMIN")]
public sealed class SaleController : ControllerBase
{
    private readonly SaleQueryService _saleQueryService;

    public SaleController(SaleQueryService saleQueryService)
    {
        _saleQueryService = saleQueryService;
    }

    [HttpGet]
    public PageResponse<SaleSummary> List(
        [FromQuery] Guid? productId,
        [FromQuery] DateTime? from,
        [FromQuery] DateTime? to,
        [FromQuery] int? page,
        [FromQuery] int? size) =>
        _saleQueryService.List(productId, from, to, PageParams.Page(page), PageParams.Size(size));

    [HttpGet("{id:guid}")]
    public SaleDetail Get(Guid id) => _saleQueryService.Get(id);
}
