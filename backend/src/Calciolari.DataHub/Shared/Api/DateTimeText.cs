using System.Globalization;

namespace Calciolari.DataHub.Shared.Api;

public static class DateTimeText
{
    public static string? Iso(DateTime? value) =>
        value is null ? null : Iso(value.Value);

    public static string Iso(DateTime value) =>
        value.ToString("yyyy-MM-dd'T'HH:mm:ss", CultureInfo.InvariantCulture);
}
