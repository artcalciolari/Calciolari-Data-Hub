package br.com.calciolari.datahub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaRepositories
public class DatahubApplication {

	public static void main(String[] args) {
		SpringApplication.run(DatahubApplication.class, args);
	}
}
