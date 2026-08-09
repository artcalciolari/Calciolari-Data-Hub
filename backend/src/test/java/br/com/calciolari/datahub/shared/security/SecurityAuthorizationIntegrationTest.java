package br.com.calciolari.datahub.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.calciolari.datahub.support.PostgresTestSupport;

@SpringBootTest
class SecurityAuthorizationIntegrationTest {

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		PostgresTestSupport.registerDataSource(registry);
		registry.add("datahub.security.enabled", () -> "true");
		registry.add("datahub.security.users",
				() -> "viewer:viewer-pass:VIEWER,importer:importer-pass:IMPORTER|VIEWER,admin:admin-pass:ADMIN|IMPORTER|VIEWER");
	}

	@Autowired
	WebApplicationContext context;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void unauthenticatedApiReturns401() throws Exception {
		mockMvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
	}

	@Test
	void viewerCanReadDashboard() throws Exception {
		mockMvc.perform(get("/api/dashboard").with(httpBasic("viewer", "viewer-pass")))
				.andExpect(status().isOk());
	}

	@Test
	void viewerCannotAccessActuatorMetrics() throws Exception {
		mockMvc.perform(get("/actuator/metrics").with(httpBasic("viewer", "viewer-pass")))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanAccessActuatorMetrics() throws Exception {
		mockMvc.perform(get("/actuator/metrics").with(httpBasic("admin", "admin-pass")))
				.andExpect(status().isOk());
	}

	@Test
	void healthRemainsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}
}
