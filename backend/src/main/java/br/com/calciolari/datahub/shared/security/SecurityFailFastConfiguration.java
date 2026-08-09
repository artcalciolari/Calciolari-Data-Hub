package br.com.calciolari.datahub.shared.security;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Fails fast when production-like profiles run without authentication configured.
 * Controlled-network local/dev keeps {@code datahub.security.enabled=false}.
 */
@Configuration
public class SecurityFailFastConfiguration {

	@Bean
	@ConditionalOnProperty(name = "datahub.security.require-enabled", havingValue = "true")
	ApplicationRunner requireSecurityEnabled(SecurityProperties security, Environment env) {
		return args -> {
			if (!security.isEnabled()) {
				throw new IllegalStateException(
						"Profile requires datahub.security.enabled=true (active="
								+ String.join(",", env.getActiveProfiles()) + "). "
								+ "Do not expose the API without authentication.");
			}
			if (security.userEntries().isEmpty()) {
				throw new IllegalStateException(
						"datahub.security.enabled=true but datahub.security.users is empty. "
								+ "Configure at least one user before starting.");
			}
		};
	}

	@Bean
	@ConditionalOnProperty(name = "datahub.security.enabled", havingValue = "true")
	ApplicationRunner requireUsersWhenEnabled(SecurityProperties security) {
		return args -> {
			if (security.userEntries().isEmpty()) {
				throw new IllegalStateException(
						"datahub.security.enabled=true but datahub.security.users is empty.");
			}
		};
	}
}
