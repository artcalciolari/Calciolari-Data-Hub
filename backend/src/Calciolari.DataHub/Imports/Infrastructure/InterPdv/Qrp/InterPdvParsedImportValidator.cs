using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Shared.Api;

namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// Deterministic validations over a ParsedImport. Source item totals are
/// preserved; calculated values exist only for comparison.
/// </summary>
public sealed class InterPdvParsedImportValidator
{
    public const string RuleVersion = "interpdv-validation-v1";

    private readonly decimal _moneyTolerance;
    private readonly decimal _quantityTolerance;

    public InterPdvParsedImportValidator()
        : this(0.01m, 0.001m)
    {
    }

    public InterPdvParsedImportValidator(decimal moneyTolerance, decimal quantityTolerance)
    {
        _moneyTolerance = moneyTolerance;
        _quantityTolerance = quantityTolerance;
    }

    public IReadOnlyList<ParseIssue> Validate(ParsedImport parsed)
    {
        ArgumentNullException.ThrowIfNull(parsed);
        var issues = new List<ParseIssue>();

        var sourceQty = parsed.Totals.SourceQuantityTotal;
        var parsedQty = parsed.Totals.ParsedQuantityTotal;
        if (sourceQty is not null && parsedQty is not null)
        {
            var diff = Math.Abs(parsedQty.Value - sourceQty.Value);
            if (diff <= _quantityTolerance)
            {
                issues.Add(new ParseIssue(
                    "SOURCE_QUANTITY_MATCH",
                    IssueSeverity.Info,
                    IssueStage.Validation,
                    SourceLocator.Empty,
                    "sourceValue=" + DecimalText.ToPlainString(sourceQty.Value)
                    + " calculatedValue=" + DecimalText.ToPlainString(parsedQty.Value)
                    + " difference=" + DecimalText.ToPlainString(diff)
                    + " tolerance=" + DecimalText.ToPlainString(_quantityTolerance)
                    + " ruleVersion=" + RuleVersion));
            }
            else
            {
                issues.Add(new ParseIssue(
                    "SOURCE_QUANTITY_MISMATCH",
                    IssueSeverity.Error,
                    IssueStage.Validation,
                    SourceLocator.Empty,
                    "sourceValue=" + DecimalText.ToPlainString(sourceQty.Value)
                    + " calculatedValue=" + DecimalText.ToPlainString(parsedQty.Value)
                    + " difference=" + DecimalText.ToPlainString(diff)
                    + " tolerance=" + DecimalText.ToPlainString(_quantityTolerance)
                    + " ruleVersion=" + RuleVersion));
            }
        }

        foreach (var movement in parsed.Movements)
        {
            ValidateLineTotal(movement, issues);
            ValidateStockContinuity(movement, issues);
        }

        return issues;
    }

    private void ValidateLineTotal(ParsedMovement movement, List<ParseIssue> issues)
    {
        if (movement.Quantity is null || movement.UnitPrice is null || movement.Total is null)
        {
            return;
        }

        var discount = movement.DiscountPercentage ?? 0m;
        var factor = 1m - decimal.Round(discount / 100m, 8, MidpointRounding.AwayFromZero);
        var calculated = decimal.Round(movement.Quantity.Value * movement.UnitPrice.Value * factor, 2, MidpointRounding.AwayFromZero);
        var diff = Math.Abs(calculated - movement.Total.Value);
        var code = diff <= _moneyTolerance ? "LINE_TOTAL_MATCH" : "LINE_TOTAL_MISMATCH";
        var severity = diff <= _moneyTolerance ? IssueSeverity.Info : IssueSeverity.Warning;
        issues.Add(new ParseIssue(
            code,
            severity,
            IssueStage.Validation,
            movement.SourceLocator,
            "sourceValue=" + DecimalText.ToPlainString(movement.Total.Value)
            + " calculatedValue=" + DecimalText.ToPlainString(calculated)
            + " difference=" + DecimalText.ToPlainString(diff)
            + " tolerance=" + DecimalText.ToPlainString(_moneyTolerance)
            + " ruleVersion=" + RuleVersion));
    }

    private void ValidateStockContinuity(ParsedMovement movement, List<ParseIssue> issues)
    {
        if (movement.PreviousStock is null || movement.ResultingStock is null || movement.Quantity is null)
        {
            return;
        }

        if (movement.Direction != MovementDirection.Out)
        {
            return;
        }

        var expected = movement.PreviousStock.Value - movement.Quantity.Value;
        var diff = Math.Abs(expected - movement.ResultingStock.Value);
        if (diff > _quantityTolerance)
        {
            issues.Add(new ParseIssue(
                "STOCK_CONTINUITY_MISMATCH",
                IssueSeverity.Warning,
                IssueStage.Validation,
                movement.SourceLocator,
                "previous=" + DecimalText.ToPlainString(movement.PreviousStock.Value)
                + " quantity=" + DecimalText.ToPlainString(movement.Quantity.Value)
                + " resulting=" + DecimalText.ToPlainString(movement.ResultingStock.Value)
                + " expected=" + DecimalText.ToPlainString(expected)));
        }
    }
}
