using System.Globalization;
using System.Runtime.CompilerServices;

namespace Calciolari.DataHub.Shared.Api;

public static class DecimalText
{
    [MethodImpl(MethodImplOptions.NoInlining)]
    public static string? ToPlainString(decimal? value)
    {
        if (value is null)
        {
            return null;
        }

        return ToPlainString(value.Value);
    }

    public static string ToPlainString(decimal value) =>
        value.ToString(CultureInfo.InvariantCulture);

    public static string StripTrailingZerosToPlain(decimal value)
    {
        var text = value.ToString(CultureInfo.InvariantCulture);
        if (text.Contains('.', StringComparison.Ordinal))
        {
            text = text.TrimEnd('0').TrimEnd('.');
        }

        return text;
    }

    [MethodImpl(MethodImplOptions.NoInlining)]
    public static string? StripTrailingZerosToPlain(decimal? value)
    {
        if (value is null)
        {
            return null;
        }

        return StripTrailingZerosToPlain(value.Value);
    }
}
