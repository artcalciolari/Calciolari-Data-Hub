package br.com.calciolari.datahub.imports.domain.parser;

/**
 * Aggregate counts from a parse. Null means the metric was not observed / not
 * applicable yet — never invent zeros to look complete.
 */
public record ParsedImportStats(
		Integer pages,
		Integer lines,
		Integer uniqueSales,
		Integer entries,
		Integer exits
) {
	public static ParsedImportStats empty() {
		return new ParsedImportStats(null, null, null, null, null);
	}
}
