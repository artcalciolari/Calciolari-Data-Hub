using System.Globalization;
using System.Text.RegularExpressions;
using Calciolari.DataHub.Imports.Domain.Parser;

namespace Calciolari.DataHub.Imports.Infrastructure.InterPdv.Qrp;

/// <summary>
/// Maps EMF text runs onto the InterPDV "Relatório de Auditoria" layout.
/// Port of PoC parseQrp column association (x-distance &lt; 75).
/// </summary>
public sealed class InterPdvReportLayoutMapper
{
    public const string Source = "INTERPDV";
    public const string ParserName = "interpdv-qrp";
    public const string ParserVersion = "interpdv-qrp-v1";

    private static readonly Regex Product = new(@"^Produto:\s*(\d+)\s*-\s*(.+)$", RegexOptions.IgnoreCase | RegexOptions.Compiled);
    private static readonly Regex Sale = new(@"^Venda Numero:\s*(\d+)$", RegexOptions.IgnoreCase | RegexOptions.Compiled);
    private static readonly Regex Manufacturer = new(@"^Fabricante:\s*(.*)$", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private static readonly string[] Headers =
    [
        "Preço", "Desconto", "Total Item", "Data", "Hora", "Saidas", "Entradas", "Anterior", "Posterior"
    ];

    private const int ColumnXTolerance = 75;
    private static readonly CultureInfo PtBr = CultureInfo.GetCultureInfo("pt-BR");

    private readonly int _columnTolerance;

    public InterPdvReportLayoutMapper()
        : this(ColumnXTolerance)
    {
    }

    public InterPdvReportLayoutMapper(int columnTolerance)
    {
        if (columnTolerance < 1)
        {
            throw new ArgumentOutOfRangeException(nameof(columnTolerance), "columnTolerance must be >= 1");
        }

        _columnTolerance = columnTolerance;
    }

    public ParsedImport Map(IReadOnlyList<EmfTextRun> items, int pageCount)
    {
        ArgumentNullException.ThrowIfNull(items);
        var issues = new List<ParseIssue>();

        var product = FirstMatch(items, Product);
        if (product is null)
        {
            issues.Add(Issue("PRODUCT_FIELD_MISSING", IssueSeverity.Fatal, IssueStage.Layout,
                "Campo Produto não encontrado no relatório."));
            return EmptyFatal(issues, pageCount);
        }

        var externalProductId = product.Groups[1].Value;
        var productName = product.Groups[2].Value.Trim();
        var manufacturer = FirstMatch(items, Manufacturer)?.Groups[1].Value.Trim();

        var headerByPage = new Dictionary<int, Dictionary<string, int>>();
        foreach (var it in items)
        {
            if (Headers.Contains(it.Text, StringComparer.Ordinal))
            {
                if (!headerByPage.TryGetValue(it.PageIndex, out var map))
                {
                    map = new Dictionary<string, int>(StringComparer.Ordinal);
                    headerByPage[it.PageIndex] = map;
                }

                map[it.Text] = it.X;
            }
        }

        var groups = new Dictionary<string, List<EmfTextRun>>();
        foreach (var it in items)
        {
            var key = it.PageIndex + ":" + it.Y;
            if (!groups.TryGetValue(key, out var group))
            {
                group = [];
                groups[key] = group;
            }

            group.Add(it);
        }

        var movements = new List<ParsedMovement>();
        var recordIndex = 0;
        foreach (var group in groups.Values)
        {
            EmfTextRun? sale = null;
            Match? saleMatcher = null;
            foreach (var it in group)
            {
                var m = Sale.Match(it.Text);
                if (m.Success)
                {
                    sale = it;
                    saleMatcher = m;
                    break;
                }
            }

            if (sale is null || saleMatcher is null)
            {
                continue;
            }

            headerByPage.TryGetValue(sale.PageIndex, out var hp);
            hp ??= new Dictionary<string, int>(StringComparer.Ordinal);
            var row = new Dictionary<string, string>(StringComparer.Ordinal)
            {
                ["saleNumber"] = saleMatcher.Groups[1].Value
            };
            foreach (var it in group)
            {
                if (ReferenceEquals(it, sale))
                {
                    continue;
                }

                string? best = null;
                var dist = int.MaxValue;
                foreach (var header in Headers)
                {
                    if (!hp.TryGetValue(header, out var hx))
                    {
                        continue;
                    }

                    var d = Math.Abs(it.X - hx);
                    if (d < dist)
                    {
                        dist = d;
                        best = header;
                    }
                }

                if (best is not null && dist < _columnTolerance)
                {
                    row[best] = it.Text;
                }
            }

            row.TryGetValue("Saidas", out var saidas);
            row.TryGetValue("Entradas", out var entradas);
            var exits = BrazilianDecimalParser.Parse(saidas);
            var entries = BrazilianDecimalParser.Parse(entradas);
            var direction = ResolveDirection(exits, entries);

            row.TryGetValue("Data", out var date);
            row.TryGetValue("Hora", out var time);
            var occurredAt = ParseOccurredAt(date, time, issues, recordIndex);

            row.TryGetValue("Preço", out var preco);
            row.TryGetValue("Desconto", out var desconto);
            row.TryGetValue("Total Item", out var totalItem);
            row.TryGetValue("Anterior", out var anterior);
            row.TryGetValue("Posterior", out var posterior);

            movements.Add(new ParsedMovement(
                recordIndex,
                direction,
                externalProductId,
                productName,
                row["saleNumber"],
                occurredAt,
                QuantityForDirection(direction, exits, entries),
                BrazilianDecimalParser.Parse(preco),
                BrazilianDecimalParser.Parse(desconto),
                BrazilianDecimalParser.Parse(totalItem),
                BrazilianDecimalParser.Parse(anterior),
                BrazilianDecimalParser.Parse(posterior),
                manufacturer,
                new SourceLocator(sale.PageIndex + 1, recordIndex, null, "y=" + sale.Y)));
            recordIndex++;
        }

        var sourceQuantityTotal = FindSourceQuantityTotal(items);
        var parsedQuantityTotal = SumOutQuantities(movements);
        var parsedRevenueTotal = SumOutTotals(movements);
        DateTime? first = movements
            .Select(m => m.OccurredAt)
            .Where(v => v is not null)
            .Min();
        DateTime? last = movements
            .Select(m => m.OccurredAt)
            .Where(v => v is not null)
            .Max();

        var uniqueSales = movements
            .Select(m => m.ExternalSaleId)
            .Where(id => id is not null)
            .Distinct()
            .Count();
        var entryCount = movements.Count(m => m.Direction == MovementDirection.In);
        var exitCount = movements.Count(m => m.Direction == MovementDirection.Out);

        if (movements.Count == 0)
        {
            issues.Add(Issue("NO_SALE_ROWS", IssueSeverity.Error, IssueStage.Layout,
                "Nenhuma linha de venda reconhecida."));
        }

        return new ParsedImport(
            Source,
            ParserName,
            ParserVersion,
            externalProductId,
            productName,
            movements,
            new ParsedImportTotals(
                sourceQuantityTotal,
                parsedQuantityTotal,
                null,
                parsedRevenueTotal,
                first,
                last),
            new ParsedImportStats(pageCount, movements.Count, uniqueSales, entryCount, exitCount),
            issues);
    }

    private static MovementDirection ResolveDirection(decimal? exits, decimal? entries)
    {
        var hasExit = exits is not null;
        var hasEntry = entries is not null;
        if (hasExit && !hasEntry)
        {
            return MovementDirection.Out;
        }

        if (hasEntry && !hasExit)
        {
            return MovementDirection.In;
        }

        if (hasExit && hasEntry)
        {
            return MovementDirection.Out;
        }

        return MovementDirection.Unknown;
    }

    private static decimal? QuantityForDirection(MovementDirection direction, decimal? exits, decimal? entries) =>
        direction switch
        {
            MovementDirection.Out => exits,
            MovementDirection.In => entries,
            MovementDirection.Return or MovementDirection.Unknown => exits ?? entries,
            _ => exits ?? entries
        };

    private static decimal SumOutQuantities(IEnumerable<ParsedMovement> movements) =>
        movements
            .Where(m => m.Direction == MovementDirection.Out && m.Quantity is not null)
            .Select(m => m.Quantity!.Value)
            .DefaultIfEmpty(0m)
            .Sum();

    private static decimal SumOutTotals(IEnumerable<ParsedMovement> movements) =>
        movements
            .Where(m => m.Direction == MovementDirection.Out && m.Total is not null)
            .Select(m => m.Total!.Value)
            .DefaultIfEmpty(0m)
            .Sum();

    private static decimal? FindSourceQuantityTotal(IReadOnlyList<EmfTextRun> items)
    {
        for (var i = 0; i < items.Count; i++)
        {
            if (string.Equals(items[i].Text, "Total de Vendas:", StringComparison.OrdinalIgnoreCase))
            {
                var limit = Math.Min(i + 6, items.Count);
                for (var j = i + 1; j < limit; j++)
                {
                    var value = BrazilianDecimalParser.Parse(items[j].Text);
                    if (value is not null)
                    {
                        return value;
                    }
                }
            }
        }

        return null;
    }

    private static DateTime? ParseOccurredAt(string? date, string? time, List<ParseIssue> issues, int recordIndex)
    {
        if (string.IsNullOrWhiteSpace(date) || string.IsNullOrWhiteSpace(time))
        {
            return null;
        }

        if (DateTime.TryParseExact(
                date.Trim() + " " + time.Trim(),
                "dd/MM/yyyy HH:mm:ss",
                PtBr,
                DateTimeStyles.None,
                out var parsed))
        {
            return DateTime.SpecifyKind(parsed, DateTimeKind.Unspecified);
        }

        issues.Add(new ParseIssue(
            "INVALID_DATETIME",
            IssueSeverity.Warning,
            IssueStage.Mapping,
            new SourceLocator(null, recordIndex, null, null),
            "Data/hora inválida: " + date + " " + time));
        return null;
    }

    private static Match? FirstMatch(IReadOnlyList<EmfTextRun> items, Regex pattern)
    {
        foreach (var it in items)
        {
            var m = pattern.Match(it.Text);
            if (m.Success)
            {
                return m;
            }
        }

        return null;
    }

    private static ParseIssue Issue(string code, IssueSeverity severity, IssueStage stage, string message) =>
        new(code, severity, stage, SourceLocator.Empty, message);

    private static ParsedImport EmptyFatal(List<ParseIssue> issues, int pageCount) =>
        new(
            Source,
            ParserName,
            ParserVersion,
            null,
            null,
            Array.Empty<ParsedMovement>(),
            ParsedImportTotals.Empty,
            new ParsedImportStats(pageCount, 0, 0, 0, 0),
            issues);
}
