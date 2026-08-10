package br.com.calciolari.datahub;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class DatahubApplicationMainTest {

	@Test
	void mainDelegatesToSpringApplication() {
		try (MockedStatic<SpringApplication> mocked = mockStatic(SpringApplication.class)) {
			mocked.when(() -> SpringApplication.run(DatahubApplication.class, new String[] {"--x"}))
					.thenReturn(null);
			DatahubApplication.main(new String[] {"--x"});
			mocked.verify(() -> SpringApplication.run(DatahubApplication.class, new String[] {"--x"}));
		}
	}
}
