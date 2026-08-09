package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParseAttemptRepository extends JpaRepository<ParseAttemptEntity, UUID> {
	Optional<ParseAttemptEntity> findFirstByRawArtifactIdAndParserNameAndParserVersionOrderByAttemptCountDesc(
			UUID rawArtifactId, String parserName, String parserVersion);

	List<ParseAttemptEntity> findByRawArtifactId(UUID rawArtifactId);
}
