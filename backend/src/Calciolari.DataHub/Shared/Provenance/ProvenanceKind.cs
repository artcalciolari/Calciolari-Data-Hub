namespace Calciolari.DataHub.Shared.Provenance;

/// <summary>
/// Structural provenance for the MVP. Inferred data must never silently fill or
/// overwrite canonical source fields.
/// </summary>
public enum ProvenanceKind
{
    SourceData,
    CalculatedData,
    InferredData
}

public static class ProvenanceKindNames
{
    public const string SourceData = "SOURCE_DATA";
    public const string CalculatedData = "CALCULATED_DATA";
    public const string InferredData = "INFERRED_DATA";

    public static string WireName(this ProvenanceKind kind) => kind switch
    {
        ProvenanceKind.SourceData => SourceData,
        ProvenanceKind.CalculatedData => CalculatedData,
        ProvenanceKind.InferredData => InferredData,
        _ => throw new ArgumentOutOfRangeException(nameof(kind), kind, null)
    };
}
