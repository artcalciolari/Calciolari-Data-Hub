using System.Globalization;
using System.Text.RegularExpressions;

namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// Locale-tolerant decimal parser ported from the PoC brNumber helper,
/// returning decimal instead of IEEE-754.
/// </summary>
public static class BrazilianDecimalParser
{
    private static readonly Regex CurrencyPrefix = new(@"R\$\s*", RegexOptions.IgnoreCase | RegexOptions.Compiled);
    private static readonly Regex NonNumeric = new(@"[^0-9+\-.]", RegexOptions.Compiled);

    public static decimal? Parse(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
        {
            return null;
        }

        var v = CurrencyPrefix.Replace(raw.Trim(), string.Empty, 1).Trim();
        if (v.Contains(',') && v.Contains('.'))
        {
            v = v.Replace(".", string.Empty).Replace(',', '.');
        }
        else if (v.Contains(','))
        {
            v = v.Replace(',', '.');
        }

        v = NonNumeric.Replace(v, string.Empty);
        if (v.Length == 0 || v is "-" or "+" or ".")
        {
            return null;
        }

        return decimal.TryParse(v, NumberStyles.Number, CultureInfo.InvariantCulture, out var parsed)
            ? parsed
            : null;
    }
}
