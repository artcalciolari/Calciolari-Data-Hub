package br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp;

/**
 * Byte range of one embedded EMF page inside a QRP container.
 * Offsets observed in {@code docs/poc/index.html} ({@code findEmfPages}).
 */
public record EmfPage(int start, int length) {

	public EmfPage {
		if (start < 0) {
			throw new IllegalArgumentException("start must be >= 0");
		}
		if (length <= 0) {
			throw new IllegalArgumentException("length must be > 0");
		}
	}

	public int endExclusive() {
		return Math.addExact(start, length);
	}
}
