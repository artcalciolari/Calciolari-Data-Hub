package br.com.calciolari.datahub.imports.domain.hints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

class DomainHintsRecordsTest {

	@Test
	void incompleteDateNullYearAndBounds() {
		IncompleteDate d = new IncompleteDate(15, 8);
		assertFalse(d.hasYear());
		assertThrows(IllegalArgumentException.class, () -> new IncompleteDate(0, 1));
		assertThrows(IllegalArgumentException.class, () -> new IncompleteDate(1, 13));
		assertThrows(IllegalArgumentException.class, () -> new IncompleteDate(32, 1));
		assertThrows(IllegalArgumentException.class, () -> new IncompleteDate(1, 0));
		IncompleteDate withYear = new IncompleteDate(1, 1, OptionalInt.of(2024));
		assertTrue(withYear.hasYear());
		IncompleteDate nullYear = new IncompleteDate(1, 1, null);
		assertFalse(nullYear.hasYear());
	}

	@Test
	void filenameHintsEmptyAndNullSafe() {
		FilenameHints empty = FilenameHints.empty(null);
		assertTrue(empty.isEmpty());
		FilenameHints withPeriod = new FilenameHints(
				"f.qrp",
				Optional.of(new IncompleteDateRange(new IncompleteDate(1, 1), new IncompleteDate(2, 2))),
				Optional.empty());
		assertFalse(withPeriod.isEmpty());
		FilenameHints withSingle = new FilenameHints(
				"s.qrp",
				Optional.empty(),
				Optional.of(new IncompleteDate(20, 7)));
		assertFalse(withSingle.isEmpty());
		FilenameHints nullSafe = new FilenameHints("x", null, null);
		assertTrue(nullSafe.isEmpty());
	}

	@Test
	void incompleteDateRangeRequiresEnds() {
		assertThrows(NullPointerException.class,
				() -> new IncompleteDateRange(null, new IncompleteDate(1, 1)));
	}
}
