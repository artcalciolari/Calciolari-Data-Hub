package br.com.calciolari.datahub.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.calciolari.datahub.imports.support.FixturePackage;
import br.com.calciolari.datahub.support.PostgresTestSupport;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
	@Autowired
	JdbcTemplate jdbcTemplate;
	@Autowired
	ObjectMapper objectMapper;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		PostgresTestSupport.cleanDatabase(jdbcTemplate);
		PostgresTestSupport.cleanRawStorage();
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

	@Test
	void importerCannotReprocessButAdminCan() throws Exception {
		byte[] bytes = FixturePackage.requireBytes("fixture-a");
		MockMultipartFile file = new MockMultipartFile(
				"files", "AUDITORIA.QRP", "application/octet-stream", bytes);

		MvcResult upload = mockMvc.perform(multipart("/api/imports/qrp").file(file)
						.with(httpBasic("importer", "importer-pass")))
				.andReturn();
		org.junit.jupiter.api.Assertions.assertEquals(202, upload.getResponse().getStatus(),
				upload.getResponse().getContentAsString());
		JsonNode body = objectMapper.readTree(upload.getResponse().getContentAsString());
		String fileId = body.path("files").path(0).path("id").asText();

		mockMvc.perform(post("/api/imports/files/{fileId}/reprocess", fileId)
						.with(httpBasic("importer", "importer-pass")))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/imports/files/{fileId}/reprocess", fileId)
						.with(httpBasic("admin", "admin-pass")))
				.andExpect(status().isOk());
	}
}
