package br.com.calciolari.datahub.imports.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.imports.api.ImportDtos.ImportAcceptedResponse;
import br.com.calciolari.datahub.imports.api.ImportDtos.ImportFileDetail;
import br.com.calciolari.datahub.imports.api.ImportDtos.ImportJobResponse;
import br.com.calciolari.datahub.imports.application.ImportIngestionService;
import br.com.calciolari.datahub.imports.application.ImportQueryService;
import br.com.calciolari.datahub.imports.application.ImportedFileResult;
import br.com.calciolari.datahub.shared.api.PageParams;
import br.com.calciolari.datahub.shared.api.PageResponse;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

	private static final int MAX_FILES = 20;
	private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;

	private final ImportIngestionService ingestionService;
	private final ImportQueryService queryService;

	public ImportController(ImportIngestionService ingestionService, ImportQueryService queryService) {
		this.ingestionService = ingestionService;
		this.queryService = queryService;
	}

	@PostMapping(path = "/qrp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ImportAcceptedResponse> upload(
			@RequestPart("files") List<MultipartFile> files) {
		if (files == null || files.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "files[] is required");
		}
		if (files.size() > MAX_FILES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "too many files (max " + MAX_FILES + ")");
		}
		for (MultipartFile file : files) {
			validateFile(file);
		}

		UUID jobId = ingestionService.createJob();
		List<ImportedFileResult> results = new ArrayList<>();
		for (MultipartFile file : files) {
			try {
				results.add(ingestionService.ingestIntoJob(
						jobId, file.getInputStream(), file.getOriginalFilename()));
			}
			catch (IOException ex) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read upload stream");
			}
		}
		ingestionService.completeJob(jobId);
		ImportJobResponse job = queryService.getJob(jobId);
		ImportAcceptedResponse body = new ImportAcceptedResponse(jobId, job.status(), job.files());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
				.header("Location", "/api/imports/" + jobId)
				.body(body);
	}

	@GetMapping
	public PageResponse<ImportJobResponse> list(
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size) {
		return queryService.listJobs(PageParams.page(page), PageParams.size(size));
	}

	@GetMapping("/{jobId}")
	public ImportJobResponse get(@PathVariable UUID jobId) {
		return queryService.getJob(jobId);
	}

	@GetMapping("/{jobId}/files/{fileId}")
	public ImportFileDetail getFile(@PathVariable UUID jobId, @PathVariable UUID fileId) {
		return queryService.getFile(jobId, fileId);
	}

	private static void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "empty file rejected");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds size limit");
		}
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
		if (!name.toLowerCase(Locale.ROOT).endsWith(".qrp")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only .qrp extension is accepted");
		}
	}
}
