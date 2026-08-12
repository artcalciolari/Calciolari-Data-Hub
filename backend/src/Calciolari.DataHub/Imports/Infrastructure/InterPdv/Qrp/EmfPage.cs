namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// Byte range of one embedded EMF page inside a QRP container.
/// Offsets observed in docs/poc/index.html (findEmfPages).
/// </summary>
public sealed record EmfPage(int Start, int Length)
{
    public int EndExclusive => checked(Start + Length);

    public static EmfPage Create(int start, int length)
    {
        if (start < 0)
        {
            throw new ArgumentOutOfRangeException(nameof(start), "start must be >= 0");
        }

        if (length <= 0)
        {
            throw new ArgumentOutOfRangeException(nameof(length), "length must be > 0");
        }

        return new EmfPage(start, length);
    }
}
