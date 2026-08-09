package br.com.calciolari.datahub.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SecurityUnitTest {

	@Test
	void securityPropertiesUserEntries() {
		SecurityProperties props = new SecurityProperties();
		assertFalse(props.isEnabled());
		assertEquals(List.of(), props.userEntries());
		props.setUsers(null);
		assertEquals("", props.getUsers());
		assertEquals(List.of(), props.userEntries());
		props.setUsers("  a:b:VIEWER , ,c:d:ADMIN|VIEWER ");
		assertEquals(List.of("a:b:VIEWER", "c:d:ADMIN|VIEWER"), props.userEntries());
		props.setEnabled(true);
		assertTrue(props.isEnabled());
	}

	@Test
	void corsPropertiesOriginList() {
		CorsProperties props = new CorsProperties();
		assertEquals(List.of(), props.originList());
		props.setAllowedOrigins(null);
		assertEquals("", props.getAllowedOrigins());
		assertEquals(List.of(), props.originList());
		props.setAllowedOrigins(" http://a , ,http://b ");
		assertEquals(List.of("http://a", "http://b"), props.originList());
	}

	@Test
	void parseUserValidAndInvalid() {
		var encoder = new BCryptPasswordEncoder();
		UserDetails user = SecurityConfiguration.parseUser("alice:secret:VIEWER|IMPORTER", encoder);
		assertEquals("alice", user.getUsername());
		assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_VIEWER")));

		assertThrows(IllegalArgumentException.class,
				() -> SecurityConfiguration.parseUser("bad", encoder));
		assertThrows(IllegalArgumentException.class,
				() -> SecurityConfiguration.parseUser("::VIEWER", encoder));
		assertThrows(IllegalArgumentException.class,
				() -> SecurityConfiguration.parseUser(":secret:VIEWER", encoder));
	}

	@Test
	void failFastRequireSecurityEnabled() throws Exception {
		SecurityFailFastConfiguration config = new SecurityFailFastConfiguration();
		SecurityProperties props = new SecurityProperties();
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("production");

		props.setEnabled(false);
		var runnerDisabled = config.requireSecurityEnabled(props, env);
		ApplicationArguments args = new DefaultApplicationArguments(new String[] {});
		assertThrows(IllegalStateException.class, () -> runnerDisabled.run(args));

		props.setEnabled(true);
		props.setUsers("");
		assertThrows(IllegalStateException.class, () -> runnerDisabled.run(args));

		props.setUsers("admin:x:ADMIN");
		runnerDisabled.run(args);
	}

	@Test
	void failFastRequireUsersWhenEnabled() throws Exception {
		SecurityFailFastConfiguration config = new SecurityFailFastConfiguration();
		SecurityProperties props = new SecurityProperties();
		props.setEnabled(true);
		props.setUsers("");
		var runner = config.requireUsersWhenEnabled(props);
		assertThrows(IllegalStateException.class,
				() -> runner.run(new DefaultApplicationArguments(new String[] {})));
		props.setUsers("u:p:VIEWER");
		runner.run(new DefaultApplicationArguments(new String[] {}));
	}

	@Test
	void corsConfigurationSourceRegistersWhenOriginsConfigured() {
		CorsProperties props = new CorsProperties();
		props.setAllowedOrigins("http://localhost:5173");
		var source = new SecurityConfiguration().corsConfigurationSource(props);
		var request = new MockHttpServletRequest("OPTIONS", "/api/dashboard");
		request.addHeader("Origin", "http://localhost:5173");
		var config = source.getCorsConfiguration(request);
		assertNotNull(config);
		assertEquals(List.of("http://localhost:5173"), config.getAllowedOrigins());

		var emptySource = new SecurityConfiguration().corsConfigurationSource(new CorsProperties());
		assertNotNull(emptySource);
	}

	@Test
	void userDetailsServiceDisabledAndEnabled() {
		SecurityConfiguration config = new SecurityConfiguration();
		var encoder = new BCryptPasswordEncoder();

		SecurityProperties disabled = new SecurityProperties();
		disabled.setEnabled(false);
		var disabledUsers = config.userDetailsService(disabled, encoder);
		assertThrows(UsernameNotFoundException.class, () -> disabledUsers.loadUserByUsername("missing"));

		SecurityProperties enabled = new SecurityProperties();
		enabled.setEnabled(true);
		enabled.setUsers("alice:secret:VIEWER");
		assertEquals("alice", config.userDetailsService(enabled, encoder).loadUserByUsername("alice").getUsername());
	}

	@Test
	void nullFieldBranchesViaReflection() throws Exception {
		SecurityProperties sec = new SecurityProperties();
		var users = SecurityProperties.class.getDeclaredField("users");
		users.setAccessible(true);
		users.set(sec, null);
		assertEquals(List.of(), sec.userEntries());

		CorsProperties cors = new CorsProperties();
		var origins = CorsProperties.class.getDeclaredField("allowedOrigins");
		origins.setAccessible(true);
		origins.set(cors, null);
		assertEquals(List.of(), cors.originList());
	}

	@Test
	void parseUserSkipsBlankRoles() {
		var encoder = new BCryptPasswordEncoder();
		UserDetails user = SecurityConfiguration.parseUser("bob:pw:VIEWER||ADMIN", encoder);
		assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
	}

	@Test
	void parseUserRejectsBlankUsernameOrPassword() {
		var encoder = new BCryptPasswordEncoder();
		assertThrows(IllegalArgumentException.class,
				() -> SecurityConfiguration.parseUser("user: :VIEWER", encoder));
		assertThrows(IllegalArgumentException.class,
				() -> SecurityConfiguration.parseUser(" :pw:VIEWER", encoder));
		assertThrows(IllegalArgumentException.class,
				() -> SecurityConfiguration.parseUser("user:pw:", encoder));
		assertThrows(IllegalArgumentException.class,
				() -> SecurityConfiguration.parseUser("user:pw:|||", encoder));
	}
}
