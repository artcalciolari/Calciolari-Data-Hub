package br.com.calciolari.datahub.shared.api;

import org.springframework.web.server.ResponseStatusException;

public final class PageParams {

	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;

	private PageParams() {
	}

	public static int page(Integer page) {
		int value = page == null ? 0 : page;
		if (value < 0) {
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST, "page must be >= 0");
		}
		return value;
	}

	public static int size(Integer size) {
		int value = size == null ? DEFAULT_SIZE : size;
		if (value < 1 || value > MAX_SIZE) {
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST,
					"size must be between 1 and " + MAX_SIZE);
		}
		return value;
	}
}
