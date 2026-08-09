package br.com.calciolari.datahub.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PageParamsTest {

	@Test
	void pageDefaultsAndRejectsNegative() {
		assertEquals(0, PageParams.page(null));
		assertEquals(3, PageParams.page(3));
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> PageParams.page(-1));
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
	}

	@Test
	void sizeDefaultsAndRejectsOutOfRange() {
		assertEquals(PageParams.DEFAULT_SIZE, PageParams.size(null));
		assertEquals(50, PageParams.size(50));
		assertThrows(ResponseStatusException.class, () -> PageParams.size(0));
		assertThrows(ResponseStatusException.class, () -> PageParams.size(PageParams.MAX_SIZE + 1));
	}

	@Test
	void constructorIsPrivate() throws Exception {
		var ctor = PageParams.class.getDeclaredConstructor();
		assertTrue(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers()));
		ctor.setAccessible(true);
		ctor.newInstance();
	}
}
