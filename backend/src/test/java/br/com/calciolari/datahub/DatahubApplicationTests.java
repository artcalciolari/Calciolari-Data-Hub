package br.com.calciolari.datahub;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import br.com.calciolari.datahub.support.PostgresTestSupport;

@SpringBootTest
class DatahubApplicationTests {

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		PostgresTestSupport.registerDataSource(registry);
	}

	@Test
	void contextLoads() {
	}
}
