namespace Calciolari.DataHub.Imports.Domain.Hints;

/// <summary>
/// Inclusive period hint from a filename such as 01_07-20_07.
/// Provenance: INFERRED_DATA only.
/// </summary>
public sealed record IncompleteDateRange(IncompleteDate Start, IncompleteDate End);
