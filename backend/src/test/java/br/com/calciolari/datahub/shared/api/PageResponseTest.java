package br.com.calciolari.datahub.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageResponseTest {

	@Test
	void ofComputesTotalPages() {
		PageResponse<String> page = PageResponse.of(List.of("a", "b"), 0, 2, 5);
		assertEquals(3, page.totalPages());
		assertEquals(5, page.totalElements());
		assertEquals(0, page.page());
		assertEquals(2, page.size());
	}

	@Test
	void ofHandlesZeroSizeWithoutDivisionByZero() {
		PageResponse<String> page = PageResponse.of(List.of(), 0, 0, 0);
		assertEquals(0, page.totalPages());
	}
}
