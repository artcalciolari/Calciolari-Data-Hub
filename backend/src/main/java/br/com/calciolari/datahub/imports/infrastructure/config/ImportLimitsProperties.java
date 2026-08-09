package br.com.calciolari.datahub.imports.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "datahub.imports")
public class ImportLimitsProperties {

	/** Max files per multipart upload. */
	private int maxFiles = 20;

	/** Max bytes per individual .QRP file. */
	private long maxFileBytes = 32L * 1024 * 1024;

	public int getMaxFiles() {
		return maxFiles;
	}

	public void setMaxFiles(int maxFiles) {
		this.maxFiles = maxFiles;
	}

	public long getMaxFileBytes() {
		return maxFileBytes;
	}

	public void setMaxFileBytes(long maxFileBytes) {
		this.maxFileBytes = maxFileBytes;
	}
}
