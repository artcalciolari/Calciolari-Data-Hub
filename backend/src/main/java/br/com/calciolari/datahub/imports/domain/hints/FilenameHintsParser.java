package br.com.calciolari.datahub.imports.domain.hints;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort filename hint extractor. Never throws; ambiguous names yield empty hints.
 *
 * <p>Documented patterns (anchored within the basename, extension ignored for matching):
 * <ul>
 *   <li>{@code dd_MM-dd_MM} — incomplete period (no year), e.g. {@code 01_07-20_07}</li>
 *   <li>{@code dd/MM-dd/MM} — same with slashes</li>
 *   <li>{@code dd_MM} or {@code dd/MM} — single incomplete date when not part of a range</li>
 * </ul>
 *
 * Year is never inferred from the clock.
 */
public final class FilenameHintsParser {

	private static final Pattern PERIOD_UNDERSCORE = Pattern.compile(
			"(?<!\\d)(\\d{2})_(\\d{2})-(\\d{2})_(\\d{2})(?!\\d)");
	private static final Pattern PERIOD_SLASH = Pattern.compile(
			"(?<!\\d)(\\d{2})/(\\d{2})-(\\d{2})/(\\d{2})(?!\\d)");
	private static final Pattern SINGLE_UNDERSCORE = Pattern.compile("(?<!\\d)(\\d{2})_(\\d{2})(?!\\d)");
	private static final Pattern SINGLE_SLASH = Pattern.compile("(?<!\\d)(\\d{2})/(\\d{2})(?!\\d)");

	public FilenameHints parse(String originalFilename) {
		String preserved = originalFilename == null ? "" : originalFilename;
		String basename = stripDirectory(preserved);
		String stem = stripExtension(basename);

		Optional<IncompleteDateRange> period = matchPeriod(stem);
		if (period.isPresent()) {
			return new FilenameHints(preserved, period, Optional.empty());
		}

		Optional<IncompleteDate> single = matchSingle(stem);
		return new FilenameHints(preserved, Optional.empty(), single);
	}

	private static Optional<IncompleteDateRange> matchPeriod(String stem) {
		Matcher m = PERIOD_UNDERSCORE.matcher(stem);
		if (!m.find()) {
			m = PERIOD_SLASH.matcher(stem);
			if (!m.find()) {
				return Optional.empty();
			}
		}
		return toRange(m.group(1), m.group(2), m.group(3), m.group(4));
	}

	private static Optional<IncompleteDate> matchSingle(String stem) {
		Matcher m = SINGLE_UNDERSCORE.matcher(stem);
		if (!m.find()) {
			m = SINGLE_SLASH.matcher(stem);
			if (!m.find()) {
				return Optional.empty();
			}
		}
		return toDate(m.group(1), m.group(2));
	}

	private static Optional<IncompleteDateRange> toRange(String d1, String m1, String d2, String m2) {
		Optional<IncompleteDate> start = toDate(d1, m1);
		Optional<IncompleteDate> end = toDate(d2, m2);
		if (start.isEmpty() || end.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new IncompleteDateRange(start.get(), end.get()));
	}

	private static Optional<IncompleteDate> toDate(String dayText, String monthText) {
		int day;
		int month;
		try {
			day = Integer.parseInt(dayText);
			month = Integer.parseInt(monthText);
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
		if (day < 1 || day > 31 || month < 1 || month > 12) {
			return Optional.empty();
		}
		return Optional.of(new IncompleteDate(day, month));
	}

	private static String stripDirectory(String name) {
		// Only strip Windows-style directories. Forward slashes are kept so
		// documented {@code dd/MM} filename hints remain matchable (browsers
		// already send basenames without Unix path prefixes).
		int slash = name.lastIndexOf('\\');
		return slash >= 0 ? name.substring(slash + 1) : name;
	}

	private static String stripExtension(String basename) {
		int dot = basename.lastIndexOf('.');
		if (dot <= 0) {
			return basename;
		}
		return basename.substring(0, dot);
	}
}
