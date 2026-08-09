package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Locale-tolerant decimal parser ported from the PoC {@code brNumber} helper
 * ({@code docs/poc/index.html}), returning {@link BigDecimal} instead of IEEE-754.
 */
public final class BrazilianDecimalParser {

	private static final Pattern CURRENCY_PREFIX = Pattern.compile("(?i)R\\$\\s*");
	private static final Pattern NON_NUMERIC = Pattern.compile("[^0-9+\\-.]");

	private BrazilianDecimalParser() {
	}

	public static BigDecimal parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String v = CURRENCY_PREFIX.matcher(raw.trim()).replaceFirst("").trim();
		if (v.indexOf(',') >= 0 && v.indexOf('.') >= 0) {
			v = v.replace(".", "").replace(',', '.');
		}
		else if (v.indexOf(',') >= 0) {
			v = v.replace(',', '.');
		}
		v = NON_NUMERIC.matcher(v).replaceAll("");
		if (v.isEmpty() || "-".equals(v) || "+".equals(v) || ".".equals(v)) {
			return null;
		}
		try {
			return new BigDecimal(v);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}
}
