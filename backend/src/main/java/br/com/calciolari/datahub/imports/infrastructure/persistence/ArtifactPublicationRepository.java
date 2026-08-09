package br.com.calciolari.datahub.imports.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArtifactPublicationRepository extends JpaRepository<ArtifactPublicationEntity, UUID> {
	Optional<ArtifactPublicationEntity> findByRawArtifactId(UUID rawArtifactId);

	@Query("""
			select count(ap) > 0 from ArtifactPublicationEntity ap, ParsedMovementEntity pm
			where pm.parseAttemptId = ap.activeParseAttemptId
			  and pm.externalSaleId in :saleIds
			  and ap.rawArtifactId <> :artifactId
			""")
	boolean existsOverlappingPublishedSales(
			@Param("artifactId") UUID artifactId,
			@Param("saleIds") List<String> saleIds);
}
