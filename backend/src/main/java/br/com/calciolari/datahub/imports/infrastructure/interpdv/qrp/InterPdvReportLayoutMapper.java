package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.calciolari.datahub.imports.domain.parser.IssueSeverity;
import br.com.calciolari.datahub.imports.domain.parser.IssueStage;
import br.com.calciolari.datahub.imports.domain.parser.MovementDirection;
import br.com.calciolari.datahub.imports.domain.parser.ParseIssue;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImport;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportStats;
import br.com.calciolari.datahub.imports.domain.parser.ParsedImportTotals;
import br.com.calciolari.datahub.imports.domain.parser.ParsedMovement;
import br.com.calciolari.datahub.imports.domain.parser.SourceLocator;

/**
 * Maps EMF text runs onto the InterPDV "Relatório de Auditoria" layout.
 * Port of PoC {@code parseQrp} column association (x-distance &lt; 75).
 */
public final class InterPdvReportLayoutMapper {

	public static final String SOURCE = "INTERPDV";
	public static final String PARSER_NAME = "interpdv-qrp";
	public static final String PARSER_VERSION = "interpdv-qrp-v1";

	private static final Pattern PRODUCT = Pattern.compile("^Produto:\\s*(\\d+)\\s*-\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SALE = Pattern.compile("^Venda Numero:\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern EXPORT_DT = Pattern.compile("^Data/Hora:\\s*(\\d{2}/\\d{2}/\\d{4})\\s+(\\d{2}:\\d{2}:\\d{2})$", Pattern.CASE_INSENSITIVE);
	private static final Pattern MANUFACTURER = Pattern.compile("^Fabricante:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern STOCK = Pattern.compile("^Estoque Atual:\\s*(.*)$", Pattern.CASE_INSENSITIVE);

	private static final List<String> HEADERS = List.of(
			"Preço", "Desconto", "Total Item", "Data", "Hora", "Saidas", "Entradas", "Anterior", "Posterior");

	private static final int COLUMN_X_TOLERANCE = 75;
	private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter BR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

	private final int columnTolerance;

	public InterPdvReportLayoutMapper() {
		this(COLUMN_X_TOLERANCE);
	}

	public InterPdvReportLayoutMapper(int columnTolerance) {
		if (columnTolerance < 1) {
			throw new IllegalArgumentException("columnTolerance must be >= 1");
		}
		this.columnTolerance = columnTolerance;
	}

	public ParsedImport map(List<EmfTextRun> items, int pageCount) {
		Objects.requireNonNull(items, "items");
		List<ParseIssue> issues = new ArrayList<>();

		Optional<Matcher> product = firstMatch(items, PRODUCT);
		if (product.isEmpty()) {
			issues.add(issue("PRODUCT_FIELD_MISSING", IssueSeverity.FATAL, IssueStage.LAYOUT,
					"Campo Produto não encontrado no relatório."));
			return emptyFatal(issues, pageCount);
		}

		String externalProductId = product.get().group(1);
		String productName = product.get().group(2).trim();
		String manufacturer = firstMatch(items, MANUFACTURER).map(m -> m.group(1).trim()).orElse(null);

		Map<Integer, Map<String, Integer>> headerByPage = new HashMap<>();
		for (EmfTextRun it : items) {
			if (HEADERS.contains(it.text())) {
				headerByPage.computeIfAbsent(it.pageIndex(), p -> new HashMap<>()).put(it.text(), it.x());
			}
		}

		Map<String, List<EmfTextRun>> groups = new LinkedHashMap<>();
		for (EmfTextRun it : items) {
			String key = it.pageIndex() + ":" + it.y();
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
		}

		List<ParsedMovement> movements = new ArrayList<>();
		int recordIndex = 0;
		for (List<EmfTextRun> group : groups.values()) {
			EmfTextRun sale = null;
			Matcher saleMatcher = null;
			for (EmfTextRun it : group) {
				Matcher m = SALE.matcher(it.text());
				if (m.matches()) {
					sale = it;
					saleMatcher = m;
					break;
				}
			}
			if (sale == null) {
				continue;
			}

			Map<String, Integer> hp = headerByPage.getOrDefault(sale.pageIndex(), Map.of());
			Map<String, String> row = new HashMap<>();
			row.put("saleNumber", saleMatcher.group(1));
			for (EmfTextRun it : group) {
				if (it == sale) {
					continue;
				}
				String best = null;
				int dist = Integer.MAX_VALUE;
				for (String header : HEADERS) {
					Integer hx = hp.get(header);
					if (hx == null) {
						continue;
					}
					int d = Math.abs(it.x() - hx);
					if (d < dist) {
						dist = d;
						best = header;
					}
				}
				if (best != null && dist < columnTolerance) {
					row.put(best, it.text());
				}
			}

			BigDecimal exits = BrazilianDecimalParser.parse(row.get("Saidas"));
			BigDecimal entries = BrazilianDecimalParser.parse(row.get("Entradas"));
			MovementDirection direction = resolveDirection(exits, entries);

			LocalDateTime occurredAt = parseOccurredAt(row.get("Data"), row.get("Hora"), issues, recordIndex);

			movements.add(new ParsedMovement(
					recordIndex,
					direction,
					externalProductId,
					productName,
					row.get("saleNumber"),
					occurredAt,
					quantityForDirection(direction, exits, entries),
					BrazilianDecimalParser.parse(row.get("Preço")),
					BrazilianDecimalParser.parse(row.get("Desconto")),
					BrazilianDecimalParser.parse(row.get("Total Item")),
					BrazilianDecimalParser.parse(row.get("Anterior")),
					BrazilianDecimalParser.parse(row.get("Posterior")),
					manufacturer,
					new SourceLocator(sale.pageIndex() + 1, recordIndex, null, "y=" + sale.y())));
			recordIndex++;
		}

		BigDecimal sourceQuantityTotal = findSourceQuantityTotal(items);
		BigDecimal parsedQuantityTotal = sumOutQuantities(movements);
		BigDecimal parsedRevenueTotal = sumOutTotals(movements);
		LocalDateTime first = movements.stream()
				.map(ParsedMovement::occurredAt)
				.filter(Objects::nonNull)
				.min(Comparator.naturalOrder())
				.orElse(null);
		LocalDateTime last = movements.stream()
				.map(ParsedMovement::occurredAt)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(null);

		long uniqueSales = movements.stream()
				.map(ParsedMovement::externalSaleId)
				.filter(Objects::nonNull)
				.distinct()
				.count();
		int entryCount = (int) movements.stream().filter(m -> m.direction() == MovementDirection.IN).count();
		int exitCount = (int) movements.stream().filter(m -> m.direction() == MovementDirection.OUT).count();

		if (movements.isEmpty()) {
			issues.add(issue("NO_SALE_ROWS", IssueSeverity.ERROR, IssueStage.LAYOUT,
					"Nenhuma linha de venda reconhecida."));
		}

		return new ParsedImport(
				SOURCE,
				PARSER_NAME,
				PARSER_VERSION,
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
				new ParsedImportStats(pageCount, movements.size(), (int) uniqueSales, entryCount, exitCount),
				issues);
	}

	private static MovementDirection resolveDirection(BigDecimal exits, BigDecimal entries) {
		boolean hasExit = exits != null;
		boolean hasEntry = entries != null;
		if (hasExit && !hasEntry) {
			return MovementDirection.OUT;
		}
		if (hasEntry && !hasExit) {
			return MovementDirection.IN;
		}
		if (hasExit && hasEntry) {
			// PoC treats both columns; prefer explicit non-null exits for audit sales rows.
			return MovementDirection.OUT;
		}
		return MovementDirection.UNKNOWN;
	}

	private static BigDecimal quantityForDirection(MovementDirection direction, BigDecimal exits, BigDecimal entries) {
		return switch (direction) {
			case OUT -> exits;
			case IN -> entries;
			case RETURN, UNKNOWN -> exits != null ? exits : entries;
		};
	}

	private static BigDecimal sumOutQuantities(List<ParsedMovement> movements) {
		return movements.stream()
				.filter(m -> m.direction() == MovementDirection.OUT && m.quantity() != null)
				.map(ParsedMovement::quantity)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private static BigDecimal sumOutTotals(List<ParsedMovement> movements) {
		return movements.stream()
				.filter(m -> m.direction() == MovementDirection.OUT && m.total() != null)
				.map(ParsedMovement::total)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	/**
	 * PoC/fixture evidence: label {@code Total de Vendas:} followed by a nearby numeric text run
	 * (observed as a separate EMF text on the same report footers).
	 */
	private static BigDecimal findSourceQuantityTotal(List<EmfTextRun> items) {
		for (int i = 0; i < items.size(); i++) {
			if ("Total de Vendas:".equalsIgnoreCase(items.get(i).text())) {
				for (int j = i + 1; j < Math.min(i + 6, items.size()); j++) {
					BigDecimal value = BrazilianDecimalParser.parse(items.get(j).text());
					if (value != null) {
						return value;
					}
				}
			}
		}
		return null;
	}

	private static LocalDateTime parseOccurredAt(String date, String time, List<ParseIssue> issues, int recordIndex) {
		if (date == null || date.isBlank() || time == null || time.isBlank()) {
			return null;
		}
		try {
			LocalDate d = LocalDate.parse(date.trim(), BR_DATE);
			LocalTime t = LocalTime.parse(time.trim(), BR_TIME);
			return LocalDateTime.of(d, t);
		}
		catch (DateTimeParseException ex) {
			issues.add(new ParseIssue(
					"INVALID_DATETIME",
					IssueSeverity.WARNING,
					IssueStage.MAPPING,
					new SourceLocator(null, recordIndex, null, null),
					"Data/hora inválida: " + date + " " + time));
			return null;
		}
	}

	private static Optional<Matcher> firstMatch(List<EmfTextRun> items, Pattern pattern) {
		for (EmfTextRun it : items) {
			Matcher m = pattern.matcher(it.text());
			if (m.matches()) {
				return Optional.of(m);
			}
		}
		return Optional.empty();
	}

	private static ParseIssue issue(String code, IssueSeverity severity, IssueStage stage, String message) {
		return new ParseIssue(code, severity, stage, SourceLocator.empty(), message);
	}

	private static ParsedImport emptyFatal(List<ParseIssue> issues, int pageCount) {
		return new ParsedImport(
				SOURCE,
				PARSER_NAME,
				PARSER_VERSION,
				null,
				null,
				List.of(),
				ParsedImportTotals.empty(),
				new ParsedImportStats(pageCount, 0, 0, 0, 0),
				issues);
	}
}
