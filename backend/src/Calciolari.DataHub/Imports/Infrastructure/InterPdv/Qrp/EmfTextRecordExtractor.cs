using System.Buffers.Binary;
using System.Text;

namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// Walks EMF records inside a page and extracts EMR_EXTTEXTOUTW text.
/// Evidence from PoC parseEmfTexts.
/// </summary>
public sealed class EmfTextRecordExtractor
{
    internal const int EmrEof = 14;
    internal const int EmrExtTextOutW = 84;
    internal const int MinExtTextOutWSize = 76;
    internal const int MaxChars = 10_000;
    internal const int MaxRecordsPerPage = 100_000;

    private readonly int _maxRecordsPerPage;
    private readonly int _maxChars;

    public EmfTextRecordExtractor()
        : this(MaxRecordsPerPage, MaxChars)
    {
    }

    public EmfTextRecordExtractor(int maxRecordsPerPage, int maxChars)
    {
        if (maxRecordsPerPage < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxRecordsPerPage), "maxRecordsPerPage must be >= 1");
        }

        if (maxChars < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(maxChars), "maxChars must be >= 1");
        }

        _maxRecordsPerPage = maxRecordsPerPage;
        _maxChars = maxChars;
    }

    public IReadOnlyList<EmfTextRun> Extract(byte[] bytes, EmfPage page, int pageIndex)
    {
        ArgumentNullException.ThrowIfNull(bytes);
        ArgumentNullException.ThrowIfNull(page);
        if (page.EndExclusive > bytes.Length)
        {
            throw new ArgumentException("page exceeds buffer", nameof(page));
        }

        var output = new List<EmfTextRun>();
        var pos = page.Start;
        var end = page.EndExclusive;
        var guard = 0;

        while (pos + 8 <= end && guard++ < _maxRecordsPerPage)
        {
            var type = BinaryPrimitives.ReadInt32LittleEndian(bytes.AsSpan(pos, 4));
            var sizeLong = BinaryPrimitives.ReadUInt32LittleEndian(bytes.AsSpan(pos + 4, 4));
            if (sizeLong < 8 || sizeLong > int.MaxValue)
            {
                break;
            }

            var size = (int)sizeLong;
            if (pos > end - size)
            {
                break;
            }

            if (type == EmrExtTextOutW && size >= MinExtTextOutWSize)
            {
                var x = BinaryPrimitives.ReadInt32LittleEndian(bytes.AsSpan(pos + 36, 4));
                var y = BinaryPrimitives.ReadInt32LittleEndian(bytes.AsSpan(pos + 40, 4));
                var charsLong = BinaryPrimitives.ReadUInt32LittleEndian(bytes.AsSpan(pos + 44, 4));
                var offLong = BinaryPrimitives.ReadUInt32LittleEndian(bytes.AsSpan(pos + 48, 4));
                if (charsLong <= (uint)_maxChars && offLong <= int.MaxValue && charsLong <= int.MaxValue / 2)
                {
                    var chars = (int)charsLong;
                    var off = (int)offLong;
                    var from = pos + off;
                    var to = from + chars * 2;
                    if (from >= pos && to >= from && to <= pos + size)
                    {
                        var text = DecodeUtf16Le(bytes, from, to);
                        if (text.Length > 0)
                        {
                            output.Add(new EmfTextRun(pageIndex, x, y, text));
                        }
                    }
                }
            }

            pos += size;
            if (type == EmrEof)
            {
                break;
            }
        }

        return output;
    }

    private static string DecodeUtf16Le(byte[] bytes, int from, int to)
    {
        var raw = Encoding.Unicode.GetString(bytes, from, to - from);
        return raw.Replace("\u0000", string.Empty, StringComparison.Ordinal).Trim();
    }
}
