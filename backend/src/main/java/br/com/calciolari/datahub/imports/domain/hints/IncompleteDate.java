package br.com.calciolari.datahub.imports.domain.hints;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Day/month extracted from a filename when the year is absent or incomplete.
 * Never filled with the JVM "current" year.
 */
public record IncompleteDate(int day, int month, OptionalInt year) {

	public IncompleteDate(int day, int month) {
		this(day, month, OptionalInt.empty());
	}

	public IncompleteDate {
		if (day < 1 || day > 31) {
			throw new IllegalArgumentException("day out of range: " + day);
		}
		if (month < 1 || month > 12) {
			throw new IllegalArgumentException("month out of range: " + month);
		}
		year = year == null ? OptionalInt.empty() : year;
	}

	public boolean hasYear() {
		return year.isPresent();
	}
}
