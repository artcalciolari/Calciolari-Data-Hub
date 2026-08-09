package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RawArtifactRepository extends JpaRepository<RawArtifactEntity, UUID> {
	Optional<RawArtifactEntity> findBySha256(String sha256);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RawArtifactEntity r where r.id = :id")
	Optional<RawArtifactEntity> findWithLockById(@Param("id") UUID id);
}
