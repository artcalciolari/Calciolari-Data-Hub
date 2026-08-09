package br.com.calciolari.datahub.imports.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class ImportApiIntegrationTest {

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		PostgresTestSupport.registerDataSource(registry);
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
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		jdbcTemplate.execute("""
				TRUNCATE TABLE
				  sale_item, sale, product, validation_result, parsed_movement,
				  artifact_publication, import_file, parse_attempt, import_job, raw_artifact
				RESTART IDENTITY CASCADE
				""");
	}

	@Test
	void uploadFixtureBThenQueryDashboardAndDedup() throws Exception {
		byte[] bytes = FixturePackage.requireBytes("fixture-b");
		MockMultipartFile file = new MockMultipartFile(
				"files",
				"AUDITORIA 41, 01_07-20_07.QRP",
				"application/octet-stream",
				bytes);

		MvcResult upload = mockMvc.perform(multipart("/api/imports/qrp").file(file))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/imports/")))
				.andExpect(jsonPath("$.status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.files[0].status").value("IMPORTED"))
				.andReturn();

		JsonNode body = objectMapper.readTree(upload.getResponse().getContentAsString());
		String jobId = body.path("jobId").asText();
		assertTrue(jobId.length() > 10);

		mockMvc.perform(get("/api/imports/{jobId}", jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.files.length()").value(1));

		mockMvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].externalId").value("41"))
				.andExpect(jsonPath("$.content[0].name").value("MOLHO POMODORO"));

		mockMvc.perform(get("/api/dashboard"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.salesCount").value(93))
				.andExpect(jsonPath("$.itemCount").value(134))
				.andExpect(jsonPath("$.quantityTotal").value("52.986"))
				.andExpect(jsonPath("$.revenueTotal").value("3013.07"))
				.andExpect(jsonPath("$.topProducts.length()").value(1))
				.andExpect(jsonPath("$.topProducts[0].name").value("MOLHO POMODORO"))
				.andExpect(jsonPath("$.topProducts[0].externalId").value("41"))
				.andExpect(jsonPath("$.topProducts[0].quantity").value("52.986"))
				.andExpect(jsonPath("$.topProducts[0].revenue").value("3013.07"));

		MockMultipartFile copy = new MockMultipartFile(
				"files",
				"copy-b.QRP",
				"application/octet-stream",
				bytes);
		mockMvc.perform(multipart("/api/imports/qrp").file(copy))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.files[0].deduplicated").value(true));

		mockMvc.perform(get("/api/sales").param("size", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(93));

		String saleId = objectMapper.readTree(
				mockMvc.perform(get("/api/sales").param("size", "1"))
						.andExpect(status().isOk())
						.andReturn()
						.getResponse()
						.getContentAsString())
				.path("content").get(0).path("id").asText();

		MvcResult saleDetail = mockMvc.perform(get("/api/sales/{id}", saleId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isArray())
				.andReturn();
		assertEquals(true, objectMapper.readTree(saleDetail.getResponse().getContentAsString())
				.path("items").isArray());

		mockMvc.perform(multipart("/api/imports/qrp")
						.file(new MockMultipartFile("files", "nope.txt", MediaType.TEXT_PLAIN_VALUE, "x".getBytes())))
				.andExpect(status().isBadRequest());
	}
}
