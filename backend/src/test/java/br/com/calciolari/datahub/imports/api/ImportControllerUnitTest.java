package br.com.calciolari.datahub.imports.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.imports.api.ImportDtos.ImportAcceptedResponse;
import br.com.calciolari.datahub.imports.api.ImportDtos.ImportFileSummary;
import br.com.calciolari.datahub.imports.api.ImportDtos.ImportJobResponse;
import br.com.calciolari.datahub.imports.api.ImportDtos.ReprocessResponse;
import br.com.calciolari.datahub.imports.application.ImportIngestionService;
import br.com.calciolari.datahub.imports.application.ImportIngestionService.ReprocessResult;
import br.com.calciolari.datahub.imports.application.ImportQueryService;
import br.com.calciolari.datahub.imports.application.ImportedFileResult;
import br.com.calciolari.datahub.imports.infrastructure.config.ImportLimitsProperties;
import br.com.calciolari.datahub.shared.api.PageResponse;

class ImportControllerUnitTest {

	ImportIngestionService ingestion;
	ImportQueryService query;
	ImportLimitsProperties limits;
	ImportController controller;

	@BeforeEach
	void setUp() {
		ingestion = mock(ImportIngestionService.class);
		query = mock(ImportQueryService.class);
		limits = new ImportLimitsProperties();
		limits.setMaxFiles(2);
		limits.setMaxFileBytes(100);
		controller = new ImportController(ingestion, query, limits);
	}

	@Test
	void uploadRejectsNullEntryInFilesList() {
		MockMultipartFile a = qrp("a.qrp", new byte[] {1});
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> controller.upload(java.util.Arrays.asList(a, null))));
	}

	@Test
	void uploadRejectsNullEmptyAndTooManyFiles() {
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> controller.upload(null)));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> controller.upload(List.of())));

		MockMultipartFile a = qrp("a.qrp", new byte[] {1});
		MockMultipartFile b = qrp("b.qrp", new byte[] {1});
		MockMultipartFile c = qrp("c.qrp", new byte[] {1});
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> controller.upload(List.of(a, b, c))));
	}

	@Test
	void validateFileBranches() {
		assertEquals(HttpStatus.BAD_REQUEST,
				status(() -> controller.upload(List.of(new MockMultipartFile("files", "x.qrp", null, new byte[0])))));
		assertEquals(HttpStatus.CONTENT_TOO_LARGE,
				status(() -> controller.upload(List.of(qrp("big.qrp", new byte[101])))));
		assertEquals(HttpStatus.BAD_REQUEST,
				status(() -> controller.upload(List.of(
						new MockMultipartFile("files", "nope.txt", null, new byte[] {1})))));
		assertEquals(HttpStatus.BAD_REQUEST,
				status(() -> controller.upload(List.of(
						new MockMultipartFile("files", null, null, new byte[] {1})))));
	}

	@Test
	void uploadHappyPathAndStreamFailure() throws Exception {
		UUID jobId = UUID.randomUUID();
		when(ingestion.createJob()).thenReturn(jobId);
		ImportedFileResult result = new ImportedFileResult(
				jobId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"a".repeat(64), "a.qrp", false, true, "SUCCEEDED", "IMPORTED", "VALID", 1, null, null);
		when(ingestion.ingestIntoJob(eq(jobId), any(), eq("a.qrp"))).thenReturn(result);
		ImportJobResponse job = new ImportJobResponse(jobId, "SUCCEEDED", null, null, List.of(
				new ImportFileSummary(result.importFileId(), "a.qrp", "IMPORTED", false, null,
						result.parseAttemptId(), null, null)));
		when(query.getJob(jobId)).thenReturn(job);

		var response = controller.upload(List.of(qrp("a.qrp", new byte[] {1})));
		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
		ImportAcceptedResponse body = response.getBody();
		assertEquals(jobId, body.jobId());
		assertEquals("/api/imports/" + jobId, response.getHeaders().getLocation().toString());
		verify(ingestion).completeJob(jobId);

		MultipartFile broken = mock(MultipartFile.class);
		when(broken.isEmpty()).thenReturn(false);
		when(broken.getSize()).thenReturn(1L);
		when(broken.getOriginalFilename()).thenReturn("x.qrp");
		when(broken.getInputStream()).thenThrow(new IOException("boom"));
		assertEquals(HttpStatus.BAD_REQUEST, status(() -> controller.upload(List.of(broken))));
	}

	@Test
	void listGetFileAndReprocessDelegate() {
		when(query.listJobs(0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20, 0));
		assertEquals(0, controller.list(null, null).totalElements());

		UUID jobId = UUID.randomUUID();
		UUID fileId = UUID.randomUUID();
		ImportJobResponse job = new ImportJobResponse(jobId, "SUCCEEDED", null, null, List.of());
		when(query.getJob(jobId)).thenReturn(job);
		assertEquals(jobId, controller.get(jobId).id());

		when(query.getFile(jobId, fileId)).thenReturn(mock(ImportDtos.ImportFileDetail.class));
		controller.getFile(jobId, fileId);

		ReprocessResult rr = new ReprocessResult(
				fileId, UUID.randomUUID(), null, UUID.randomUUID(), true, "VALID", "IMPORTED", 2);
		when(ingestion.reprocess(fileId)).thenReturn(rr);
		ReprocessResponse body = controller.reprocess(fileId);
		assertEquals(fileId, body.importFileId());
		assertEquals(2, body.recordsFound());
	}

	@Test
	void validateFileRejectsNullEntryInList() {
		assertEquals(HttpStatus.BAD_REQUEST,
				status(() -> controller.upload(java.util.Arrays.asList((MultipartFile) null))));
	}

	@Test
	void validationDtoDecimal() {
		assertEquals(null, ImportDtos.ValidationDto.decimal(null));
		assertEquals("1.50", ImportDtos.ValidationDto.decimal(new java.math.BigDecimal("1.50")));
	}

	private static MockMultipartFile qrp(String name, byte[] bytes) {
		return new MockMultipartFile("files", name, "application/octet-stream", bytes);
	}

	private static HttpStatus status(Runnable action) {
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, action::run);
		return HttpStatus.valueOf(ex.getStatusCode().value());
	}
}
