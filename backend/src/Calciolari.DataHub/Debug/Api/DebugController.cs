using Calciolari.DataHub.Shared.Api;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Calciolari.DataHub.Debug.Api;

[ApiController]
[Route("api/debug")]
[Authorize(Roles = "VIEWER,IMPORTER,ADMIN")]
public sealed class DebugController : ControllerBase
{
    private readonly DataHubOptions _options;
    private readonly IDatasetResetService _reset;

    public DebugController(DataHubOptions options, IDatasetResetService reset)
    {
        _options = options;
        _reset = reset;
    }

    [HttpGet]
    public DebugStatusResponse Status() => new(_options.DebugEnabled);

    [HttpPost("reset-dataset")]
    [Authorize(Roles = "ADMIN")]
    public DatasetResetResponse Reset()
    {
        if (!_options.DebugEnabled)
        {
            throw new ApiException(StatusCodes.Status404NotFound, "debug mode is disabled");
        }

        return _reset.Reset();
    }
}
