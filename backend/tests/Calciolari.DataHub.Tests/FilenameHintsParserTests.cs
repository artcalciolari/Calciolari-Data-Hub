using Calciolari.DataHub.Imports.Domain.Hints;

namespace Calciolari.DataHub.Tests;

public sealed class FilenameHintsParserTests
{
    private readonly FilenameHintsParser _parser = new();

    [Fact]
    public void ParsesIncompletePeriodWithoutInventingYear()
    {
        var hints = _parser.Parse("AUDITORIA 41, 01_07-20_07.QRP");
        Assert.Equal("AUDITORIA 41, 01_07-20_07.QRP", hints.OriginalFilename);
        Assert.Equal("41", hints.ProductCodeHint);
        Assert.NotNull(hints.PeriodHint);
        Assert.Equal(1, hints.PeriodHint!.Start.Day);
        Assert.Equal(7, hints.PeriodHint.Start.Month);
        Assert.Null(hints.PeriodHint.Start.Year);
        Assert.Equal(20, hints.PeriodHint.End.Day);
        Assert.True(hints.SingleDateHint is null);
    }

    [Fact]
    public void ReturnsEmptyForAmbiguousNames()
    {
        Assert.True(_parser.Parse("AUDITORIA.QRP").IsEmpty);
        Assert.True(_parser.Parse("AUDITORIA JULHO FINAL.QRP").IsEmpty);
        Assert.True(_parser.Parse("random-file.qrp").IsEmpty);
    }

    [Fact]
    public void NeverThrowsOnNullOrGarbage()
    {
        Assert.Equal("", _parser.Parse(null).OriginalFilename);
        Assert.True(_parser.Parse("???@@@").IsEmpty);
    }

    [Fact]
    public void ParsesSingleUnderscoreAndSlash()
    {
        var hints = _parser.Parse("relatorio_20_07.QRP");
        Assert.Equal(20, hints.SingleDateHint!.Day);
        Assert.Equal(7, hints.SingleDateHint.Month);
        Assert.NotNull(_parser.Parse("AUDITORIA 01/07-20/07.QRP").PeriodHint);
        Assert.NotNull(_parser.Parse("relatorio 20/07.QRP").SingleDateHint);
    }

    [Fact]
    public void StripsWindowsDirectoryPrefix()
    {
        Assert.NotNull(_parser.Parse("folder/AUDITORIA 01/07-20/07.QRP").PeriodHint);
        Assert.NotNull(_parser.Parse("folder/relatorio_20_07.QRP").SingleDateHint);
        Assert.NotNull(_parser.Parse("folder\\relatorio_20_07.QRP").SingleDateHint);
        Assert.NotNull(_parser.Parse("/relatorio_20_07.QRP").SingleDateHint);
        Assert.True(_parser.Parse("relatorio_20_07.QRP/").IsEmpty);
        Assert.Null(_parser.Parse("AUDITORIA.QRP").ProductCodeHint);
    }

    [Fact]
    public void InvalidDayOrMonth()
    {
        Assert.Null(FilenameHintsParser.ToDate("xx", "01"));
        Assert.Null(FilenameHintsParser.ToDate("01", "yy"));
        Assert.True(_parser.Parse("x_00_07.QRP").IsEmpty);
        Assert.True(_parser.Parse("x_01_00.QRP").IsEmpty);
        Assert.True(_parser.Parse("x_32_07.QRP").IsEmpty);
        Assert.True(_parser.Parse("x_01_13.QRP").IsEmpty);
        Assert.Null(FilenameHintsParser.ToRange("xx", "01", "02", "03"));
        Assert.Null(FilenameHintsParser.ToRange("01", "01", "xx", "03"));
    }

    [Fact]
    public void StemWithoutExtensionAndDotfile()
    {
        Assert.True(_parser.Parse("AUDITORIA").IsEmpty);
        Assert.True(_parser.Parse(".qrp").IsEmpty);
    }
}
