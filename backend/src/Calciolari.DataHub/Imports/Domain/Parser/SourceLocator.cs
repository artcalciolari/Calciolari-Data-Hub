namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Optional locator back to the source report (page/record/offset). Populated only
/// from observed parser evidence — never invented placeholders.
/// </summary>
public sealed record SourceLocator(int? Page, int? RecordIndex, long? ByteOffset, string? Detail)
{
    public static SourceLocator Empty { get; } = new(null, null, null, null);

    public override string ToString() =>
        $"SourceLocator[page={Fmt(Page)}, recordIndex={Fmt(RecordIndex)}, byteOffset={Fmt(ByteOffset)}, detail={Detail ?? "null"}]";

    private static string Fmt<T>(T? value) where T : struct => value is null ? "null" : value.Value.ToString()!;
}
