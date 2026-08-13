using Calciolari.DataHub.Imports.Application;
using Calciolari.DataHub.Shared.Api;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Calciolari.DataHub.Imports.Api;

[ApiController]
[Route("api/imports")]
public sealed class ImportController : ControllerBase
{
    private readonly ImportIngestionService _ingestionService;
    private readonly ImportQueryService _queryService;
    private readonly DataHubOptions _options;

    public ImportController(
        ImportIngestionService ingestionService,
        ImportQueryService queryService,
        DataHubOptions options)
    {
        _ingestionService = ingestionService;
        _queryService = queryService;
        _options = options;
    }

    [HttpPost("qrp")]
    [Authorize(Roles = "IMPORTER,ADMIN")]
    [RequestSizeLimit(64L * 1024 * 1024)]
    public ActionResult<ImportJobResponse> Upload([FromForm] List<IFormFile>? files)
    {
        if (files is null || files.Count == 0)
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "files[] is required");
        }

        if (files.Count > _options.MaxFiles)
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "too many files (max " + _options.MaxFiles + ")");
        }

        foreach (var file in files)
        {
            ValidateFile(file);
        }

        var jobId = _ingestionService.CreateJob();
        foreach (var file in files)
        {
            using var stream = file.OpenReadStream();
            _ingestionService.AcceptIntoJob(jobId, stream, file.FileName);
        }

        _ingestionService.CompleteJob(jobId);
        var job = _queryService.GetJob(jobId);
        Response.Headers.Location = "/api/imports/" + jobId;
        return StatusCode(StatusCodes.Status202Accepted, job);
    }

    [HttpGet]
    [Authorize(Roles = "VIEWER,IMPORTER,ADMIN")]
    public PageResponse<ImportJobResponse> List([FromQuery] int? page, [FromQuery] int? size) =>
        _queryService.ListJobs(PageParams.Page(page), PageParams.Size(size));

    [HttpGet("{jobId:guid}")]
    [Authorize(Roles = "VIEWER,IMPORTER,ADMIN")]
    public ImportJobResponse Get(Guid jobId) => _queryService.GetJob(jobId);

    [HttpGet("{jobId:guid}/files/{fileId:guid}")]
    [Authorize(Roles = "VIEWER,IMPORTER,ADMIN")]
    public ImportFileDetail GetFile(Guid jobId, Guid fileId) => _queryService.GetFile(jobId, fileId);

    [HttpPost("files/{fileId:guid}/reprocess")]
    [Authorize(Roles = "ADMIN")]
    public ReprocessResponse Reprocess(Guid fileId)
    {
        var result = _ingestionService.Reprocess(fileId);
        return new ReprocessResponse(
            result.ImportFileId,
            result.RawArtifactId,
            result.PreviousActiveParseAttemptId,
            result.ParseAttemptId,
            result.Published,
            result.ParseStatus,
            result.FileStatus,
            result.RecordsFound);
    }

    private void ValidateFile(IFormFile file)
    {
        if (file.Length <= 0)
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "empty file rejected");
        }

        if (file.Length > _options.MaxFileBytes)
        {
            throw new ApiException(StatusCodes.Status413PayloadTooLarge, "file exceeds size limit");
        }

        var name = file.FileName ?? string.Empty;
        if (!name.EndsWith(".qrp", StringComparison.OrdinalIgnoreCase))
        {
            throw new ApiException(StatusCodes.Status400BadRequest, "only .qrp extension is accepted");
        }
    }
}
