namespace Calciolari.DataHub.Persistence.Entities;

public sealed class ValidationResultEntity
{
    public Guid Id { get; set; }
    public Guid ParseAttemptId { get; set; }
    public string Code { get; set; } = string.Empty;
    public string Status { get; set; } = string.Empty;
    public decimal? SourceValue { get; set; }
    public decimal? CalculatedValue { get; set; }
    public decimal? Difference { get; set; }
    public decimal? Tolerance { get; set; }
    public string RuleVersion { get; set; } = string.Empty;
    public string? SourceLocator { get; set; }

    public ValidationResultEntity()
    {
    }

    public ValidationResultEntity(
        Guid id,
        Guid parseAttemptId,
        string code,
        string status,
        decimal? sourceValue,
        decimal? calculatedValue,
        decimal? difference,
        decimal? tolerance,
        string ruleVersion,
        string? sourceLocator)
    {
        Id = id;
        ParseAttemptId = parseAttemptId;
        Code = code;
        Status = status;
        SourceValue = sourceValue;
        CalculatedValue = calculatedValue;
        Difference = difference;
        Tolerance = tolerance;
        RuleVersion = ruleVersion;
        SourceLocator = sourceLocator;
    }
}
