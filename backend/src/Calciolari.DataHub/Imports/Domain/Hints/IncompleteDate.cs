namespace Calciolari.DataHub.Imports.Domain.Hints;

/// <summary>
/// Day/month extracted from a filename when the year is absent or incomplete.
/// Never filled with the clock's current year.
/// </summary>
public sealed record IncompleteDate(int Day, int Month, int? Year = null)
{
    public bool HasYear => Year is not null;

    public static IncompleteDate Create(int day, int month, int? year = null)
    {
        if (day < 1 || day > 31)
        {
            throw new ArgumentOutOfRangeException(nameof(day), "day out of range: " + day);
        }

        if (month < 1 || month > 12)
        {
            throw new ArgumentOutOfRangeException(nameof(month), "month out of range: " + month);
        }

        return new IncompleteDate(day, month, year);
    }
}
