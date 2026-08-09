package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RawArtifactRepository extends JpaRepository<RawArtifactEntity, UUID> {
	Optional<RawArtifactEntity> findBySha256(String sha256);
}
