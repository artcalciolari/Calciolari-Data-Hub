package br.com.calciolari.datahub.shared.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, CorsProperties.class})
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			SecurityProperties security,
			CorsConfigurationSource corsConfigurationSource) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource))
				.headers(headers -> headers
						.contentTypeOptions(Customizer.withDefaults())
						.frameOptions(frame -> frame.deny())
						.referrerPolicy(referrer -> referrer.policy(
								org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		if (!security.isEnabled()) {
			http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
			return http.build();
		}

		http
				.httpBasic(Customizer.withDefaults())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/imports/files/*/reprocess").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/imports/**").hasAnyRole("IMPORTER", "ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("VIEWER", "IMPORTER", "ADMIN")
						.requestMatchers("/api/**").hasRole("ADMIN")
						.anyRequest().authenticated());
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		CorsConfiguration config = new CorsConfiguration();
		List<String> origins = corsProperties.originList();
		if (!origins.isEmpty()) {
			config.setAllowedOrigins(origins);
			config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
			config.setAllowedHeaders(List.of("*"));
			config.setExposedHeaders(List.of("Location"));
			config.setAllowCredentials(true);
			config.setMaxAge(3600L);
			source.registerCorsConfiguration("/api/**", config);
			source.registerCorsConfiguration("/actuator/**", config);
		}
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(SecurityProperties security, PasswordEncoder encoder) {
		if (!security.isEnabled()) {
			return new InMemoryUserDetailsManager();
		}
		List<UserDetails> users = new ArrayList<>();
		for (String entry : security.userEntries()) {
			users.add(parseUser(entry, encoder));
		}
		return new InMemoryUserDetailsManager(users);
	}

	static UserDetails parseUser(String entry, PasswordEncoder encoder) {
		String[] parts = entry.split(":", 3);
		if (parts.length != 3) {
			throw new IllegalArgumentException(
					"Invalid datahub.security.users entry (expected user:pass:ROLE1|ROLE2): " + entry);
		}
		String username = parts[0].trim();
		String password = parts[1].trim();
		String[] roles = java.util.Arrays.stream(parts[2].split("\\|"))
				.map(String::trim)
				.filter(r -> !r.isEmpty())
				.toArray(String[]::new);
		if (username.isEmpty() || password.isEmpty() || roles.length == 0) {
			throw new IllegalArgumentException("Incomplete datahub.security.users entry: " + entry);
		}
		return User.builder()
				.username(username)
				.password(encoder.encode(password))
				.roles(roles)
				.build();
	}
}
