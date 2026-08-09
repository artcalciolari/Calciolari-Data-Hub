package br.com.calciolari.datahub.shared.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datahub.cors")
public class CorsProperties {

	/**
	 * Comma-separated allowed origins. Empty means same-origin only (no CORS headers).
	 * Dev may set {@code http://localhost:5173}. Prefer same-origin reverse-proxy in production.
	 */
	private String allowedOrigins = "";

	public String getAllowedOrigins() {
		return allowedOrigins;
	}

	public void setAllowedOrigins(String allowedOrigins) {
		this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins;
	}

	public List<String> originList() {
		if (allowedOrigins == null || allowedOrigins.isBlank()) {
			return List.of();
		}
		return Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
