package br.com.calciolari.datahub.imports.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datahub.raw-storage")
public class RawStorageProperties {

	/**
	 * Absolute or relative root directory for immutable raw artifacts.
	 */
	private String root = "./data/raw-storage";

	public String getRoot() {
		return root;
	}

	public void setRoot(String root) {
		this.root = root;
	}
}
