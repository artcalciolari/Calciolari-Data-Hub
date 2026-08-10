package br.com.calciolari.datahub.shared.api;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	ProblemDetail handle(ResponseStatusException ex) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), sanitize(ex.getReason()));
		detail.setTitle(ex.getStatusCode().toString());
		return detail;
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, sanitize(ex.getMessage()));
		detail.setTitle("Bad Request");
		detail.setType(URI.create("about:blank"));
		return detail;
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ProblemDetail handleMissingPart(MissingServletRequestPartException ex) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Missing required part: " + ex.getRequestPartName());
		detail.setTitle("Bad Request");
		return detail;
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail handleNotFound(NoResourceFoundException ex) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
		detail.setTitle("Not Found");
		return detail;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleGeneric(Exception ex) {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(
				HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
		detail.setTitle("Internal Server Error");
		detail.setProperties(Map.of("code", "UNEXPECTED"));
		return detail;
	}

	private static String sanitize(String message) {
		if (message == null || message.isBlank()) {
			return "Request could not be processed";
		}
		return message.length() > 500 ? message.substring(0, 500) : message;
	}
}
