using Calciolari.DataHub.Persistence;
using Calciolari.DataHub.Shared.Api;
using Microsoft.EntityFrameworkCore;

namespace Calciolari.DataHub.Imports.Application;

public sealed class ImportQueryService
{
    private readonly DataHubDbContext _db;

    public ImportQueryService(DataHubDbContext db)
    {
        _db = db;
    }

    public PageResponse<ImportJobResponse> ListJobs(int page, int size)
    {
        var query = _db.ImportJobs.AsNoTracking().OrderByDescending(j => j.CreatedAt);
        var total = query.Count();
        var jobs = query.Skip(page * size).Take(size).ToList();
        var content = jobs.Select(ToJob).ToList();
        return PageResponse<ImportJobResponse>.Of(content, page, size, total);
    }

    public ImportJobResponse GetJob(Guid jobId)
    {
        var job = _db.ImportJobs.AsNoTracking().SingleOrDefault(j => j.Id == jobId)
                  ?? throw new ApiException(StatusCodes.Status404NotFound, "Import job not found");
        return ToJob(job);
    }

    public ImportFileDetail GetFile(Guid jobId, Guid fileId)
    {
        var file = _db.ImportFiles.AsNoTracking().SingleOrDefault(f => f.Id == fileId)
                   ?? throw new ApiException(StatusCodes.Status404NotFound, "Import file not found");
        if (file.ImportJobId != jobId)
        {
            throw new ApiException(StatusCodes.Status404NotFound, "Import file not found for job");
        }

        var artifact = _db.RawArtifacts.AsNoTracking().Single(a => a.Id == file.RawArtifactId);
        var attempt = file.ParseAttemptId is null
            ? null
            : _db.ParseAttempts.AsNoTracking().SingleOrDefault(a => a.Id == file.ParseAttemptId);
        var validations = attempt is null
            ? []
            : _db.ValidationResults.AsNoTracking()
                .Where(v => v.ParseAttemptId == attempt.Id)
                .OrderBy(v => v.Code)
                .Select(v => new ValidationDto(
                    v.Code,
                    v.Status,
                    DecimalText.ToPlainString(v.SourceValue),
                    DecimalText.ToPlainString(v.CalculatedValue),
                    DecimalText.ToPlainString(v.Difference),
                    DecimalText.ToPlainString(v.Tolerance),
                    v.RuleVersion,
                    v.SourceLocator))
                .ToList();

        return new ImportFileDetail(
            file.Id,
            file.ImportJobId,
            file.RawArtifactId,
            file.ParseAttemptId,
            file.OriginalFilename,
            file.Source,
            file.Status,
            file.Deduplicated,
            file.DuplicateOfImportFileId,
            artifact.Sha256,
            artifact.ByteSize,
            attempt?.Status,
            attempt?.RecordsFound,
            attempt?.ParserName,
            attempt?.ParserVersion,
            FilenameHintsJson.Read(file.FilenameHints),
            validations,
            file.CreatedAt,
            file.CompletedAt);
    }

    private ImportJobResponse ToJob(Persistence.Entities.ImportJobEntity job)
    {
        var files = _db.ImportFiles.AsNoTracking()
            .Where(f => f.ImportJobId == job.Id)
            .OrderBy(f => f.CreatedAt)
            .ToList();
        return new ImportJobResponse(job.Id, job.Status, job.CreatedAt, job.CompletedAt, files.Select(ToFileSummary).ToList());
    }

    private ImportFileSummary ToFileSummary(Persistence.Entities.ImportFileEntity file)
    {
        var attempt = file.ParseAttemptId is null
            ? null
            : _db.ParseAttempts.AsNoTracking().SingleOrDefault(a => a.Id == file.ParseAttemptId);
        string? productName = null;
        string? productExternalId = null;
        string? parsedQuantity = null;
        string? parsedRevenue = null;
        string? sourceQuantity = null;
        string? quantityValidationStatus = null;
        if (attempt is not null)
        {
            var sample = _db.ParsedMovements.AsNoTracking()
                .Where(m => m.ParseAttemptId == attempt.Id && m.ProductName != null)
                .OrderBy(m => m.SourceRecordIndex)
                .Select(m => new { m.ProductName, m.ExternalProductId })
                .FirstOrDefault();
            if (sample is not null)
            {
                productName = sample.ProductName;
                productExternalId = sample.ExternalProductId;
            }

            var qty = _db.ValidationResults.AsNoTracking()
                .Where(v => v.ParseAttemptId == attempt.Id
                            && (v.Code == "SOURCE_QUANTITY_MATCH" || v.Code == "SOURCE_QUANTITY_MISMATCH"))
                .OrderBy(v => v.Code)
                .FirstOrDefault();
            if (qty is not null)
            {
                sourceQuantity = DecimalText.ToPlainString(qty.SourceValue);
                parsedQuantity = DecimalText.ToPlainString(qty.CalculatedValue);
                quantityValidationStatus = qty.Status;
            }

            var revenue = _db.ParsedMovements.AsNoTracking()
                .Where(m => m.ParseAttemptId == attempt.Id && m.Direction == "OUT" && m.Total != null)
                .Select(m => (decimal?)m.Total)
                .Sum();
            parsedRevenue = DecimalText.ToPlainString(revenue);
        }

        return new ImportFileSummary(
            file.Id,
            file.OriginalFilename,
            file.Status,
            file.Deduplicated,
            file.DuplicateOfImportFileId,
            file.ParseAttemptId,
            file.CreatedAt,
            file.CompletedAt,
            attempt?.RecordsFound,
            attempt?.Status,
            productName,
            productExternalId,
            parsedQuantity,
            parsedRevenue,
            sourceQuantity,
            quantityValidationStatus);
    }
}

public sealed record ImportJobResponse(
    Guid Id,
    string Status,
    DateTimeOffset CreatedAt,
    DateTimeOffset? CompletedAt,
    IReadOnlyList<ImportFileSummary> Files);

public sealed record ImportFileSummary(
    Guid Id,
    string OriginalFilename,
    string Status,
    bool Deduplicated,
    Guid? DuplicateOfImportFileId,
    Guid? ParseAttemptId,
    DateTimeOffset CreatedAt,
    DateTimeOffset? CompletedAt,
    int? RecordsFound = null,
    string? ParseStatus = null,
    string? ProductName = null,
    string? ProductExternalId = null,
    string? ParsedQuantity = null,
    string? ParsedRevenue = null,
    string? SourceQuantity = null,
    string? QuantityValidationStatus = null);

public sealed record ImportFileDetail(
    Guid Id,
    Guid JobId,
    Guid RawArtifactId,
    Guid? ParseAttemptId,
    string OriginalFilename,
    string Source,
    string Status,
    bool Deduplicated,
    Guid? DuplicateOfImportFileId,
    string Sha256,
    long ByteSize,
    string? ParseStatus,
    int? RecordsFound,
    string? ParserName,
    string? ParserVersion,
    object? FilenameHints,
    IReadOnlyList<ValidationDto> Validations,
    DateTimeOffset CreatedAt,
    DateTimeOffset? CompletedAt);

public sealed record ValidationDto(
    string Code,
    string Status,
    string? SourceValue,
    string? CalculatedValue,
    string? Difference,
    string? Tolerance,
    string RuleVersion,
    string? SourceLocator);

public sealed record ReprocessResponse(
    Guid ImportFileId,
    Guid RawArtifactId,
    Guid? PreviousActiveParseAttemptId,
    Guid ParseAttemptId,
    bool Published,
    string ParseStatus,
    string FileStatus,
    int RecordsFound);
