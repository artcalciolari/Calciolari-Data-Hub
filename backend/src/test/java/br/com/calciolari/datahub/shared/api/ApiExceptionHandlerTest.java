package br.com.calciolari.datahub.shared.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void handlesResponseStatusException() {
		ProblemDetail detail = handler.handle(new ResponseStatusException(HttpStatus.CONFLICT, "boom"));
		assertEquals(HttpStatus.CONFLICT.value(), detail.getStatus());
		assertEquals("boom", detail.getDetail());
	}

	@Test
	void handlesBlankReasonWithFallback() {
		ProblemDetail detail = handler.handle(new ResponseStatusException(HttpStatus.BAD_REQUEST, "  "));
		assertEquals("Request could not be processed", detail.getDetail());
	}

	@Test
	void truncatesLongMessages() {
		String longMsg = "x".repeat(600);
		ProblemDetail detail = handler.handleIllegalArgument(new IllegalArgumentException(longMsg));
		assertEquals(HttpStatus.BAD_REQUEST.value(), detail.getStatus());
		assertEquals(500, detail.getDetail().length());
	}

	@Test
	void handlesNullIllegalArgumentMessage() {
		ProblemDetail detail = handler.handleIllegalArgument(new IllegalArgumentException((String) null));
		assertEquals("Request could not be processed", detail.getDetail());
	}

	@Test
	void handlesNoResourceFound() {
		ProblemDetail detail = handler.handleNotFound(
				new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "resource", "/missing"));
		assertEquals(HttpStatus.NOT_FOUND.value(), detail.getStatus());
		assertEquals("Resource not found", detail.getDetail());
	}

	@Test
	void handlesMissingMultipartPart() {
		ProblemDetail detail = handler.handleMissingPart(
				new org.springframework.web.multipart.support.MissingServletRequestPartException("files"));
		assertEquals(HttpStatus.BAD_REQUEST.value(), detail.getStatus());
		assertTrue(detail.getDetail().contains("files"));
	}

	@Test
	void handlesGenericException() {
		ProblemDetail detail = handler.handleGeneric(new RuntimeException("secret"));
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), detail.getStatus());
		assertEquals("Unexpected server error", detail.getDetail());
		assertTrue(detail.getProperties().containsKey("code"));
	}
}
