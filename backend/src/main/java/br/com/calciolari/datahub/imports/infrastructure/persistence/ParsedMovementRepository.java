package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParsedMovementRepository extends JpaRepository<ParsedMovementEntity, UUID> {
	List<ParsedMovementEntity> findByParseAttemptId(UUID parseAttemptId);
}
