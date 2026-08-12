namespace Calciolari.DataHub.Imports.Domain.Parser;

/// <summary>
/// Bytes + optional non-authoritative metadata for <see cref="IImportParser"/>.
/// originalFilename is never identity.
/// </summary>
public sealed record ParserInput(
    Stream Content,
    long ContentLength,
    string? OriginalFilename,
    string? DetectedType)
{
    public string? OriginalFilenameOptional() =>
        string.IsNullOrWhiteSpace(OriginalFilename) ? null : OriginalFilename;

    public static ParserInput Create(Stream content, long contentLength, string? originalFilename, string? detectedType)
    {
        ArgumentNullException.ThrowIfNull(content);
        if (contentLength < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(contentLength), "contentLength must be >= 0");
        }

        return new ParserInput(content, contentLength, originalFilename, detectedType);
    }
}
