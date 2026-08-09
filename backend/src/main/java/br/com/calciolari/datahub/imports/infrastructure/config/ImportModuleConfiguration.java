package br.com.calciolari.datahub.imports.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.calciolari.datahub.imports.domain.hints.FilenameHintsParser;
import br.com.calciolari.datahub.imports.domain.parser.ImportParser;
import br.com.calciolari.datahub.imports.infrastructure.interpdv.qrp.InterPdvQrpParser;

@Configuration
public class ImportModuleConfiguration {

	@Bean
	ImportParser importParser() {
		return new InterPdvQrpParser();
	}

	@Bean
	FilenameHintsParser filenameHintsParser() {
		return new FilenameHintsParser();
	}
}
