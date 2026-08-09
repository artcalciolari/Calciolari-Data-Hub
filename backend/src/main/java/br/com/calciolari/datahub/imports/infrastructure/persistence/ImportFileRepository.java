package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportFileRepository extends JpaRepository<ImportFileEntity, UUID> {
	List<ImportFileEntity> findByRawArtifactIdOrderByCreatedAtAsc(UUID rawArtifactId);

	Optional<ImportFileEntity> findFirstByRawArtifactIdAndDeduplicatedFalseOrderByCreatedAtAsc(UUID rawArtifactId);
}
