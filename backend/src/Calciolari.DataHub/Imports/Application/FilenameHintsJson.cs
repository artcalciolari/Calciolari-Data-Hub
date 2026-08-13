using System.Text.Json;
using Calciolari.DataHub.Imports.Domain.Hints;
using Calciolari.DataHub.Shared.Provenance;

namespace Calciolari.DataHub.Imports.Application;

public static class FilenameHintsJson
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.Never
    };

    internal static Func<object, JsonSerializerOptions, string> Serialize { get; set; } =
        static (value, options) => JsonSerializer.Serialize(value, options);

    public static string Write(FilenameHints hints)
    {
        try
        {
            return Serialize(ToDto(hints), Options);
        }
        catch (Exception)
        {
            return "{\"originalFilename\":\"" + hints.OriginalFilename.Replace("\"", "\\\"", StringComparison.Ordinal)
                + "\",\"provenance\":\"" + ProvenanceKindNames.InferredData + "\"}";
        }
    }

    public static object? Read(string? json)
    {
        if (json is null)
        {
            return null;
        }

        try
        {
            return JsonSerializer.Deserialize<JsonElement>(json);
        }
        catch (JsonException)
        {
            return json;
        }
    }

    private static FilenameHintsDto ToDto(FilenameHints hints) =>
        new(
            hints.OriginalFilename,
            ProvenanceKindNames.InferredData,
            hints.ProductCodeHint,
            hints.PeriodHint is null
                ? null
                : new IncompleteDateRangeDto(
                    ToDateDto(hints.PeriodHint.Start),
                    ToDateDto(hints.PeriodHint.End)),
            hints.SingleDateHint is null ? null : ToDateDto(hints.SingleDateHint));

    private static IncompleteDateDto ToDateDto(IncompleteDate date) =>
        new(date.Day, date.Month, date.Year);

    private sealed record FilenameHintsDto(
        string OriginalFilename,
        string Provenance,
        string? ProductCodeHint,
        IncompleteDateRangeDto? PeriodHint,
        IncompleteDateDto? SingleDateHint);

    private sealed record IncompleteDateRangeDto(IncompleteDateDto Start, IncompleteDateDto End);

    private sealed record IncompleteDateDto(int Day, int Month, int? Year);
}
