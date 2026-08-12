using Calciolari.DataHub.Imports.Domain.Parser;
using Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;
using Calciolari.DataHub.Tests.Support;

namespace Calciolari.DataHub.Tests;

public sealed class InterPdvQrpParserTests
{
    [Fact]
    public void FixtureAMatchesAcceptanceCriteria()
    {
        Assert.True(FixturePackage.IsPresent("fixture-a"));
        var bytes = FixturePackage.RequireBytes("fixture-a");
        var parsed = new InterPdvQrpParser().Parse(new ParserInput(new MemoryStream(bytes), bytes.Length, "AUDITORIA.QRP", "QRP"));
        Assert.Equal("35", parsed.ExternalProductId);
        Assert.Equal("NHOQUE BATATA", parsed.ProductName);
        Assert.Equal(1, parsed.Stats.Pages);
        Assert.Equal(15, parsed.Stats.Lines);
        Assert.Equal(10.842m, parsed.Totals.SourceQuantityTotal);
        Assert.Equal(parsed.Totals.SourceQuantityTotal, parsed.Totals.ParsedQuantityTotal);
        var sample = parsed.Movements.First(m => m.ExternalSaleId == "134808" && m.Total == 32.59m);
        Assert.Equal(MovementDirection.Out, sample.Direction);
        Assert.Equal(new DateTime(2026, 8, 7, 12, 22, 13), sample.OccurredAt);
        Assert.Equal(0.510m, sample.Quantity);
        Assert.Equal(63.90m, sample.UnitPrice);
        Assert.False(parsed.HasFatalOrError());
        Assert.Contains(parsed.Issues, i => i.Code == "SOURCE_QUANTITY_MATCH");
    }

    [Fact]
    public void FixtureBMatchesAcceptanceCriteria()
    {
        Assert.True(FixturePackage.IsPresent("fixture-b"));
        var bytes = FixturePackage.RequireBytes("fixture-b");
        var parsed = new InterPdvQrpParser().Parse(new ParserInput(
            new MemoryStream(bytes), bytes.Length, "AUDITORIA 41, 01_07-20_07.QRP", "QRP"));
        Assert.Equal("41", parsed.ExternalProductId);
        Assert.Equal("MOLHO POMODORO", parsed.ProductName);
        Assert.Equal(4, parsed.Stats.Pages);
        Assert.Equal(134, parsed.Stats.Lines);
        Assert.Equal(93, parsed.Stats.UniqueSales);
        Assert.Equal(0, parsed.Stats.Entries);
        Assert.Equal(52.986m, parsed.Totals.SourceQuantityTotal);
        Assert.Equal(52.986m, parsed.Totals.ParsedQuantityTotal);
        Assert.Equal(3013.07m, parsed.Totals.ParsedRevenueTotal);
        Assert.Equal(new DateTime(2026, 7, 19, 13, 7, 3), parsed.Totals.LastMovementAt);
        var sample = parsed.Movements.First(m => m.ExternalSaleId == "134409");
        Assert.Equal(0.416m, sample.Quantity);
        Assert.Equal(56.90m, sample.UnitPrice);
        Assert.Equal(8m, sample.DiscountPercentage);
        Assert.Equal(21.78m, sample.Total);
        Assert.Equal(19, parsed.Totals.LastMovementAt!.Value.Day);
        Assert.False(parsed.HasFatalOrError());
    }

    [Fact]
    public void Supports_and_edge_cases()
    {
        var parser = new InterPdvQrpParser();
        Assert.True(parser.Supports(new ParserInput(Stream.Null, 0, "report.QRP", null)));
        Assert.True(parser.Supports(new ParserInput(Stream.Null, 0, "x", "qrp")));
        Assert.False(parser.Supports(new ParserInput(Stream.Null, 0, "readme.txt", null)));
        Assert.Throws<ArgumentNullException>(() => parser.Supports(null!));
        Assert.Throws<ArgumentNullException>(() => parser.Parse(null!));
        Assert.Throws<ArgumentOutOfRangeException>(() => new InterPdvQrpParser(
            new QrpContainerReader(), new EmfTextRecordExtractor(), new InterPdvReportLayoutMapper(),
            new InterPdvParsedImportValidator(), 0));

        var empty = parser.Parse(new ParserInput(new MemoryStream(), 0, "x.qrp", "QRP"));
        Assert.Contains(empty.Issues, i => i.Code == "NO_EMF_PAGES");

        var tiny = new InterPdvQrpParser(new QrpContainerReader(), new EmfTextRecordExtractor(),
            new InterPdvReportLayoutMapper(), new InterPdvParsedImportValidator(), 4);
        Assert.Throws<ArgumentException>(() =>
            tiny.Parse(new ParserInput(new MemoryStream([1, 2, 3, 4, 5]), 5, "x.qrp", "QRP")));
        Assert.Throws<ArgumentException>(() =>
            tiny.Parse(new ParserInput(new MemoryStream([1, 2, 3, 4, 5]), 0, "x.qrp", "QRP")));

        var broken = new ThrowingStream();
        Assert.Throws<IOException>(() => parser.Parse(new ParserInput(broken, 1, "x.qrp", "QRP")));

        var garbage = Enumerable.Range(0, 256).Select(i => (byte)(i * 37)).ToArray();
        var noise = parser.Parse(new ParserInput(new MemoryStream(garbage), garbage.Length, "noise.qrp", "QRP"));
        Assert.Empty(noise.Movements);
        Assert.Contains(noise.Issues, i => i.Code == "NO_EMF_PAGES");
    }

    [Fact]
    public void Reader_and_extractor_limits()
    {
        Assert.Throws<ArgumentOutOfRangeException>(() => new QrpContainerReader(0));
        Assert.Throws<ArgumentOutOfRangeException>(() => new EmfTextRecordExtractor(0, 10));
        Assert.Throws<ArgumentOutOfRangeException>(() => new EmfTextRecordExtractor(10, 0));
        Assert.Throws<ArgumentOutOfRangeException>(() => new InterPdvReportLayoutMapper(0));
        Assert.False(QrpContainerReader.IsEmfAt([], 0));
        Assert.Throws<ArgumentNullException>(() => new QrpContainerReader().FindEmfPages(null!));
        Assert.Empty(new QrpContainerReader().FindEmfPages([1, 2, 3]));
        var extractor = new EmfTextRecordExtractor();
        Assert.Throws<ArgumentNullException>(() => extractor.Extract(null!, EmfPage.Create(0, 8), 0));
        Assert.Throws<ArgumentException>(() => extractor.Extract(new byte[4], EmfPage.Create(0, 8), 0));
        Assert.Empty(extractor.Extract(new byte[16], EmfPage.Create(0, 16), 0));
    }

    [Fact]
    public void Decimal_parser_and_validator()
    {
        Assert.Equal(56.90m, BrazilianDecimalParser.Parse("R$ 56,90"));
        Assert.Equal(3013.07m, BrazilianDecimalParser.Parse("3.013,07"));
        Assert.Equal(52.986m, BrazilianDecimalParser.Parse("52,986"));
        Assert.Equal(12.34m, BrazilianDecimalParser.Parse("12.34"));
        Assert.Null(BrazilianDecimalParser.Parse(null));
        Assert.Null(BrazilianDecimalParser.Parse(""));
        Assert.Null(BrazilianDecimalParser.Parse("-"));
        Assert.Null(BrazilianDecimalParser.Parse("not-a-number"));

        var mapped = new InterPdvReportLayoutMapper().Map([], 1);
        Assert.Contains(mapped.Issues, i => i.Code == "PRODUCT_FIELD_MISSING");

        var validator = new InterPdvParsedImportValidator();
        var parsed = new ParsedImport(
            "INTERPDV", "interpdv-qrp", "interpdv-qrp-v1", "1", "P",
            [
                new ParsedMovement(0, MovementDirection.Out, "1", "P", "9", DateTime.Now, 1m, 10m, 0m, 10m, 5m, 4m, null, SourceLocator.Empty),
                new ParsedMovement(1, MovementDirection.Out, "1", "P", "9", null, 1m, 10m, 0m, 99m, null, null, null, SourceLocator.Empty),
                new ParsedMovement(2, MovementDirection.In, "1", "P", null, null, 1m, 10m, null, 10m, 5m, 1m, null, SourceLocator.Empty),
                new ParsedMovement(3, MovementDirection.Unknown, "1", "P", null, null, null, null, null, null, null, null, null, SourceLocator.Empty)
            ],
            new ParsedImportTotals(2m, 2m, null, 20m, null, null),
            new ParsedImportStats(1, 4, 1, 1, 2),
            []);
        var issues = validator.Validate(parsed);
        Assert.Contains(issues, i => i.Code == "SOURCE_QUANTITY_MATCH");
        Assert.Contains(issues, i => i.Code == "LINE_TOTAL_MATCH");
        Assert.Contains(issues, i => i.Code == "LINE_TOTAL_MISMATCH");

        var mismatchQty = parsed with { Totals = new ParsedImportTotals(1m, 99m, null, null, null, null) };
        Assert.Contains(validator.Validate(mismatchQty), i => i.Code == "SOURCE_QUANTITY_MISMATCH");

        var stock = parsed with
        {
            Movements =
            [
                new ParsedMovement(0, MovementDirection.Out, "1", "P", "9", null, 1m, 10m, 0m, 10m, 5m, 1m, null, SourceLocator.Empty)
            ]
        };
        Assert.Contains(validator.Validate(stock), i => i.Code == "STOCK_CONTINUITY_MISMATCH");
    }

    [Fact]
    public void Layout_mapper_no_sale_rows()
    {
        var items = new List<EmfTextRun>
        {
            new(0, 0, 0, "Produto: 1 - TESTE"),
            new(0, 0, 10, "Fabricante: X")
        };
        var mapped = new InterPdvReportLayoutMapper().Map(items, 1);
        Assert.Contains(mapped.Issues, i => i.Code == "NO_SALE_ROWS");
    }

    private sealed class ThrowingStream : Stream
    {
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => 1;
        public override long Position { get; set; }
        public override void Flush() { }
        public override int Read(byte[] buffer, int offset, int count) => throw new IOException("boom");
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }
}
