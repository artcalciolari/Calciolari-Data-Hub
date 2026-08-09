package br.com.calciolari.datahub.imports.domain.parser;

/**
 * Optional locator back to the source report (page/record/offset). Populated only
 * from observed parser evidence — never invented placeholders.
 */
public record SourceLocator(
		Integer page,
		Integer recordIndex,
		Long byteOffset,
		String detail
) {
	public static SourceLocator empty() {
		return new SourceLocator(null, null, null, null);
	}
}
