package br.com.calciolari.datahub.imports.application;

import java.util.List;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.com.calciolari.datahub.imports.api.ImportDtos.ImportFileDetail;
import br.com.calciolari.datahub.imports.api.ImportDtos.ImportFileSummary;
import br.com.calciolari.datahub.imports.api.ImportDtos.ImportJobResponse;
import br.com.calciolari.datahub.imports.api.ImportDtos.ValidationDto;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportFileRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ImportJobRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ParseAttemptRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.RawArtifactRepository;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ValidationResultEntity;
import br.com.calciolari.datahub.imports.infrastructure.persistence.ValidationResultRepository;
import br.com.calciolari.datahub.shared.api.PageResponse;

@Service
public class ImportQueryService {

	private final ImportJobRepository importJobRepository;
	private final ImportFileRepository importFileRepository;
	private final ParseAttemptRepository parseAttemptRepository;
	private final RawArtifactRepository rawArtifactRepository;
	private final ValidationResultRepository validationResultRepository;
	private final ObjectMapper objectMapper;

	public ImportQueryService(
			ImportJobRepository importJobRepository,
			ImportFileRepository importFileRepository,
			ParseAttemptRepository parseAttemptRepository,
			RawArtifactRepository rawArtifactRepository,
			ValidationResultRepository validationResultRepository,
			ObjectMapper objectMapper) {
		this.importJobRepository = importJobRepository;
		this.importFileRepository = importFileRepository;
		this.parseAttemptRepository = parseAttemptRepository;
		this.rawArtifactRepository = rawArtifactRepository;
		this.validationResultRepository = validationResultRepository;
		this.objectMapper = objectMapper;
	}

	public PageResponse<ImportJobResponse> listJobs(int page, int size) {
		Page<ImportJobEntity> jobs = importJobRepository.findAll(
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
		List<ImportJobResponse> content = jobs.getContent().stream().map(this::toJob).toList();
		return PageResponse.of(content, page, size, jobs.getTotalElements());
	}

	public ImportJobResponse getJob(UUID jobId) {
		ImportJobEntity job = importJobRepository.findById(jobId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import job not found"));
		return toJob(job);
	}

	public ImportFileDetail getFile(UUID jobId, UUID fileId) {
		ImportFileEntity file = importFileRepository.findById(fileId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import file not found"));
		if (!file.getImportJobId().equals(jobId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Import file not found for job");
		}
		RawArtifactEntity artifact = rawArtifactRepository.findById(file.getRawArtifactId()).orElseThrow();
		ParseAttemptEntity attempt = file.getParseAttemptId() == null
				? null
				: parseAttemptRepository.findById(file.getParseAttemptId()).orElse(null);
		List<ValidationDto> validations = attempt == null
				? List.of()
				: validationResultRepository.findByParseAttemptIdOrderByCodeAsc(attempt.getId()).stream()
						.map(this::toValidation)
						.toList();
		Object hints = null;
		if (file.getFilenameHints() != null) {
			try {
				hints = objectMapper.readValue(file.getFilenameHints(), Object.class);
			}
			catch (RuntimeException ignored) {
				hints = file.getFilenameHints();
			}
		}
		return new ImportFileDetail(
				file.getId(),
				file.getImportJobId(),
				file.getRawArtifactId(),
				file.getParseAttemptId(),
				file.getOriginalFilename(),
				file.getSource(),
				file.getStatus(),
				file.isDeduplicated(),
				file.getDuplicateOfImportFileId(),
				artifact.getSha256(),
				artifact.getByteSize(),
				attempt == null ? null : attempt.getStatus(),
				attempt == null ? null : attempt.getRecordsFound(),
				attempt == null ? null : attempt.getParserName(),
				attempt == null ? null : attempt.getParserVersion(),
				hints,
				validations,
				file.getCreatedAt(),
				file.getCompletedAt());
	}

	private ImportJobResponse toJob(ImportJobEntity job) {
		List<ImportFileSummary> files = importFileRepository.findByImportJobIdOrderByCreatedAtAsc(job.getId())
				.stream()
				.map(f -> new ImportFileSummary(
						f.getId(),
						f.getOriginalFilename(),
						f.getStatus(),
						f.isDeduplicated(),
						f.getDuplicateOfImportFileId(),
						f.getParseAttemptId(),
						f.getCreatedAt(),
						f.getCompletedAt()))
				.toList();
		return new ImportJobResponse(job.getId(), job.getStatus(), job.getCreatedAt(), job.getCompletedAt(), files);
	}

	private ValidationDto toValidation(ValidationResultEntity entity) {
		return new ValidationDto(
				entity.getCode(),
				entity.getStatus(),
				ValidationDto.decimal(entity.getSourceValue()),
				ValidationDto.decimal(entity.getCalculatedValue()),
				ValidationDto.decimal(entity.getDifference()),
				ValidationDto.decimal(entity.getTolerance()),
				entity.getRuleVersion(),
				entity.getSourceLocator());
	}
}
