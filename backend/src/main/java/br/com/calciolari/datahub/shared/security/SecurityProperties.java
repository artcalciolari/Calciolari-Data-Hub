package br.com.calciolari.datahub.shared.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datahub.security")
public class SecurityProperties {

	/**
	 * When false (default for local/dev), the API is open — suitable only behind a
	 * controlled network. Production profile must set this to true.
	 */
	private boolean enabled = false;

	/**
	 * Comma-separated HTTP Basic users as {@code username:password:ROLE1|ROLE2}.
	 * Roles: VIEWER, IMPORTER, ADMIN. Required when {@code enabled=true}.
	 */
	private String users = "";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getUsers() {
		return users;
	}

	public void setUsers(String users) {
		this.users = users == null ? "" : users;
	}

	public List<String> userEntries() {
		if (users == null || users.isBlank()) {
			return List.of();
		}
		return Arrays.stream(users.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.toList();
	}
}
