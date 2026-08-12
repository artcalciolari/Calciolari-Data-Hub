using Calciolari.DataHub.Analytics.Application;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Calciolari.DataHub.Analytics.Api;

[ApiController]
[Route("api/dashboard")]
[Authorize(Roles = "VIEWER,IMPORTER,ADMIN")]
public sealed class DashboardController : ControllerBase
{
    private readonly DashboardQueryService _dashboardQueryService;

    public DashboardController(DashboardQueryService dashboardQueryService)
    {
        _dashboardQueryService = dashboardQueryService;
    }

    [HttpGet]
    public DashboardResponse Get(
        [FromQuery] Guid? productId,
        [FromQuery] DateTime? from,
        [FromQuery] DateTime? to) =>
        _dashboardQueryService.Summarize(productId, from, to);
}
