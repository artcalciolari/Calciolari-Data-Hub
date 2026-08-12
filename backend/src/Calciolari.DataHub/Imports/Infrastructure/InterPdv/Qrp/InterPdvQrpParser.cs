using Calciolari.DataHub.Imports.Domain.Parser;

namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// InterPDV QuickReport (.QRP) adapter. Binary layout ported from docs/poc/index.html — not invented.
/// </summary>
public sealed class InterPdvQrpParser : IImportParser
{
    public const string ParserName = InterPdvReportLayoutMapper.ParserName;
    public const string ParserVersion = InterPdvReportLayoutMapper.ParserVersion;

    private readonly QrpContainerReader _containerReader;
    private readonly EmfTextRecordExtractor _textExtractor;
    private readonly InterPdvReportLayoutMapper _layoutMapper;
    private readonly InterPdvParsedImportValidator _validator;
    private readonly long _maxBytes;

    public InterPdvQrpParser()
        : this(new QrpContainerReader(), new EmfTextRecordExtractor(), new InterPdvReportLayoutMapper(),
            new InterPdvParsedImportValidator(), 32L * 1024 * 1024)
    {
    }

    public InterPdvQrpParser(
        QrpContainerReader containerReader,
        EmfTextRecordExtractor textExtractor,
        InterPdvReportLayoutMapper layoutMapper,
        InterPdvParsedImportValidator validator,
        long maxBytes)
    {
        _containerReader = containerReader ?? throw new ArgumentNullException(nameof(containerReader));
        _textExtractor = textExtractor ?? throw new ArgumentNullException(nameof(textExtractor));
        _layoutMapper = layoutMapper ?? throw new ArgumentNullException(nameof(layoutMapper));
        _validator = validator ?? throw new ArgumentNullException(nameof(validator));
        if (maxBytes < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxBytes), "maxBytes must be >= 1");
        }

        _maxBytes = maxBytes;
    }

    public bool Supports(ParserInput input)
    {
        ArgumentNullException.ThrowIfNull(input);
        if (input.DetectedType is not null &&
            string.Equals(input.DetectedType, "QRP", StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }

        var name = input.OriginalFilename;
        return name is not null && name.EndsWith(".qrp", StringComparison.OrdinalIgnoreCase);
    }

    public ParsedImport Parse(ParserInput input)
    {
        ArgumentNullException.ThrowIfNull(input);
        var bytes = ReadLimited(input.Content, input.ContentLength);
        var pages = _containerReader.FindEmfPages(bytes);
        if (pages.Count == 0)
        {
            return Fatal("NO_EMF_PAGES", IssueStage.Container, "Nenhuma página EMF reconhecida neste QRP.");
        }

        var texts = new List<EmfTextRun>();
        for (var i = 0; i < pages.Count; i++)
        {
            texts.AddRange(_textExtractor.Extract(bytes, pages[i], i));
        }

        if (texts.Count == 0)
        {
            return Fatal("NO_EMF_TEXT", IssueStage.Emf, "QRP reconhecido sem registros de texto EMF.");
        }

        var mapped = _layoutMapper.Map(texts, pages.Count);
        var merged = mapped.Issues.ToList();
        merged.AddRange(_validator.Validate(mapped));
        return new ParsedImport(
            mapped.Source,
            mapped.ParserName,
            mapped.ParserVersion,
            mapped.ExternalProductId,
            mapped.ProductName,
            mapped.Movements,
            mapped.Totals,
            mapped.Stats,
            merged);
    }

    private byte[] ReadLimited(Stream content, long contentLength)
    {
        try
        {
            if (contentLength > _maxBytes)
            {
                throw new ArgumentException("contentLength exceeds maxBytes (" + _maxBytes + ")");
            }

            using var buffer = new MemoryStream();
            content.CopyTo(buffer);
            var bytes = buffer.ToArray();
            if (bytes.Length > _maxBytes)
            {
                throw new ArgumentException("payload exceeds maxBytes (" + _maxBytes + ")");
            }

            return bytes;
        }
        catch (IOException ex)
        {
            throw new IOException("failed to read parser input", ex);
        }
    }

    private static ParsedImport Fatal(string code, IssueStage stage, string message) =>
        new(
            InterPdvReportLayoutMapper.Source,
            ParserName,
            ParserVersion,
            null,
            null,
            Array.Empty<ParsedMovement>(),
            ParsedImportTotals.Empty,
            ParsedImportStats.Empty,
            [new ParseIssue(code, IssueSeverity.Fatal, stage, SourceLocator.Empty, message)]);
}
