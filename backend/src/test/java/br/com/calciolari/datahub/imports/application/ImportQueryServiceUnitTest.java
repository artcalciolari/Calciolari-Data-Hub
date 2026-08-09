package br.com.calciolari.datahub.imports.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ValidationResultRepository;
import tools.jackson.databind.ObjectMapper;

class ImportQueryServiceUnitTest {

	@Test
	void getFileLeavesHintsNullWhenNotStored() {
		UUID jobId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, jobId, artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		file.setFilenameHints(null);
		ImportFileRepository files = mock(ImportFileRepository.class);
		when(files.findById(fileId)).thenReturn(Optional.of(file));

		RawArtifactRepository artifacts = mock(RawArtifactRepository.class);
		when(artifacts.findById(artifactId)).thenReturn(Optional.of(
				new RawArtifactEntity(artifactId, "a".repeat(64), 1L, "00/00/" + "b".repeat(64), "QRP")));

		ImportQueryService service = new ImportQueryService(
				mock(ImportJobRepository.class), files,
				mock(ParseAttemptRepository.class), artifacts,
				mock(ValidationResultRepository.class), new ObjectMapper());
		assertEquals(null, service.getFile(jobId, fileId).filenameHints());
	}

	@Test
	void getFileFallsBackWhenHintsJsonCannotBeParsed() throws Exception {
		ObjectMapper mapper = mock(ObjectMapper.class);
		when(mapper.readValue(eq("{broken"), eq(Object.class)))
				.thenThrow(new RuntimeException("bad json"));

		ImportFileRepository files = mock(ImportFileRepository.class);
		RawArtifactRepository artifacts = mock(RawArtifactRepository.class);
		ParseAttemptRepository attempts = mock(ParseAttemptRepository.class);
		ValidationResultRepository validations = mock(ValidationResultRepository.class);
		ImportJobRepository jobs = mock(ImportJobRepository.class);

		UUID jobId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		UUID artifactId = UUID.randomUUID();

		ImportFileEntity file = new ImportFileEntity(fileId, jobId, artifactId, "f.qrp", "INTERPDV", "IMPORTED");
		file.setFilenameHints("{broken");
		when(files.findById(fileId)).thenReturn(Optional.of(file));
		when(artifacts.findById(artifactId)).thenReturn(Optional.of(
				new RawArtifactEntity(artifactId, "a".repeat(64), 1L, "00/00/" + "b".repeat(64), "QRP")));
		when(validations.findByParseAttemptIdOrderByCodeAsc(any())).thenReturn(List.of());

		ImportQueryService service = new ImportQueryService(
				jobs, files, attempts, artifacts, validations, mapper);
		assertEquals("{broken", service.getFile(jobId, fileId).filenameHints());
	}

	@Test
	void getFileRejectsMismatchedJob() {
		UUID jobId = UUID.randomUUID();
		UUID otherJob = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		ImportFileEntity file = new ImportFileEntity(fileId, otherJob, UUID.randomUUID(), "f.qrp", "INTERPDV", "IMPORTED");
		ImportFileRepository files = mock(ImportFileRepository.class);
		when(files.findById(fileId)).thenReturn(Optional.of(file));

		ImportQueryService service = new ImportQueryService(
				mock(ImportJobRepository.class), files,
				mock(ParseAttemptRepository.class),
				mock(RawArtifactRepository.class),
				mock(ValidationResultRepository.class),
				new ObjectMapper());

		org.junit.jupiter.api.Assertions.assertThrows(
				ResponseStatusException.class, () -> service.getFile(jobId, fileId));
	}

	@Test
	void getFileNotFound() {
		ImportFileRepository files = mock(ImportFileRepository.class);
		when(files.findById(any())).thenReturn(Optional.empty());
		ImportQueryService service = new ImportQueryService(
				mock(ImportJobRepository.class), files,
				mock(ParseAttemptRepository.class),
				mock(RawArtifactRepository.class),
				mock(ValidationResultRepository.class),
				new ObjectMapper());
		org.junit.jupiter.api.Assertions.assertThrows(
				ResponseStatusException.class,
				() -> service.getFile(UUID.randomUUID(), UUID.randomUUID()));
	}
}
