using System.Text.RegularExpressions;

namespace Calciolari.DataHub.Imports.Domain.Hints;

/// <summary>
/// Best-effort filename hint extractor. Never throws; ambiguous names yield empty hints.
/// Year is never inferred from the clock.
/// </summary>
public sealed class FilenameHintsParser
{
    private static readonly Regex PeriodUnderscore = new(
        @"(?<!\d)(\d{2})_(\d{2})-(\d{2})_(\d{2})(?!\d)",
        RegexOptions.Compiled);

    private static readonly Regex PeriodSlash = new(
        @"(?<!\d)(\d{2})/(\d{2})-(\d{2})/(\d{2})(?!\d)",
        RegexOptions.Compiled);

    private static readonly Regex SingleUnderscore = new(
        @"(?<!\d)(\d{2})_(\d{2})(?!\d)",
        RegexOptions.Compiled);

    private static readonly Regex SingleSlash = new(
        @"(?<!\d)(\d{2})/(\d{2})(?!\d)",
        RegexOptions.Compiled);

    private static readonly Regex ProductCode = new(
        @"AUDITORIA\s+(\d+)\b",
        RegexOptions.Compiled | RegexOptions.IgnoreCase);

    public FilenameHints Parse(string? originalFilename)
    {
        var preserved = originalFilename ?? string.Empty;
        var basename = StripDirectory(preserved);
        var stem = StripExtension(basename);
        var productCodeHint = MatchProductCode(stem);

        var period = MatchPeriod(stem);
        if (period is not null)
        {
            return new FilenameHints(preserved, period, null, productCodeHint);
        }

        var single = MatchSingle(stem);
        return new FilenameHints(preserved, null, single, productCodeHint);
    }

    private static string? MatchProductCode(string stem)
    {
        var match = ProductCode.Match(stem);
        return match.Success ? match.Groups[1].Value : null;
    }

    private static IncompleteDateRange? MatchPeriod(string stem)
    {
        var match = PeriodUnderscore.Match(stem);
        if (!match.Success)
        {
            match = PeriodSlash.Match(stem);
            if (!match.Success)
            {
                return null;
            }
        }

        return ToRange(match.Groups[1].Value, match.Groups[2].Value, match.Groups[3].Value, match.Groups[4].Value);
    }

    private static IncompleteDate? MatchSingle(string stem)
    {
        var match = SingleUnderscore.Match(stem);
        if (!match.Success)
        {
            match = SingleSlash.Match(stem);
            if (!match.Success)
            {
                return null;
            }
        }

        return ToDate(match.Groups[1].Value, match.Groups[2].Value);
    }

    internal static IncompleteDateRange? ToRange(string d1, string m1, string d2, string m2)
    {
        var start = ToDate(d1, m1);
        var end = ToDate(d2, m2);
        if (start is null || end is null)
        {
            return null;
        }

        return new IncompleteDateRange(start, end);
    }

    internal static IncompleteDate? ToDate(string dayText, string monthText)
    {
        if (!int.TryParse(dayText, out var day) || !int.TryParse(monthText, out var month))
        {
            return null;
        }

        if (day < 1 || day > 31 || month < 1 || month > 12)
        {
            return null;
        }

        return IncompleteDate.Create(day, month);
    }

    private static string StripDirectory(string name)
    {
        var slash = Math.Max(name.LastIndexOf('\\'), name.LastIndexOf('/'));
        return slash >= 0 ? name[(slash + 1)..] : name;
    }

    private static string StripExtension(string basename)
    {
        var dot = basename.LastIndexOf('.');
        if (dot <= 0)
        {
            return basename;
        }

        return basename[..dot];
    }
}
