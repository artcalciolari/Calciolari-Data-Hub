using System.Buffers.Binary;

namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// Locates embedded EMF pages inside a QRP blob.
/// Evidence from PoC isEmfAt / findEmfPages.
/// </summary>
public sealed class QrpContainerReader
{
    internal const int EmrHeader = 1;
    internal const int EmfSignatureOffset = 40;
    internal const int EmfNbytesOffset = 48;
    internal const int MinHeaderWindow = 56;
    internal const int MinPageBytes = 80;

    private static readonly byte[] EmfSignature = [0x20, 0x45, 0x4D, 0x46];

    private readonly int _maxPages;

    public QrpContainerReader()
        : this(512)
    {
    }

    public QrpContainerReader(int maxPages)
    {
        if (maxPages < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxPages), "maxPages must be >= 1");
        }

        _maxPages = maxPages;
    }

    public IReadOnlyList<EmfPage> FindEmfPages(byte[] bytes)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        var pages = new List<EmfPage>();
        for (var i = 0; i + MinHeaderWindow < bytes.Length; i++)
        {
            if (!IsEmfAt(bytes, i))
            {
                continue;
            }

            var nBytesLong = BinaryPrimitives.ReadUInt32LittleEndian(bytes.AsSpan(i + EmfNbytesOffset, 4));
            if (nBytesLong > int.MaxValue)
            {
                continue;
            }

            var nBytes = (int)nBytesLong;
            if (nBytes > MinPageBytes && i <= bytes.Length - nBytes)
            {
                pages.Add(EmfPage.Create(i, nBytes));
                if (pages.Count >= _maxPages)
                {
                    break;
                }

                i += nBytes - 1;
            }
        }

        return pages;
    }

    internal static bool IsEmfAt(byte[] bytes, int i)
    {
        if (i < 0 || i + MinHeaderWindow > bytes.Length)
        {
            return false;
        }

        if (BinaryPrimitives.ReadInt32LittleEndian(bytes.AsSpan(i, 4)) != EmrHeader)
        {
            return false;
        }

        return bytes[i + EmfSignatureOffset] == EmfSignature[0]
               && bytes[i + EmfSignatureOffset + 1] == EmfSignature[1]
               && bytes[i + EmfSignatureOffset + 2] == EmfSignature[2]
               && bytes[i + EmfSignatureOffset + 3] == EmfSignature[3];
    }
}
