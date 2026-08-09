package br.com.calciolari.datahub.catalog.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
	Optional<ProductEntity> findByExternalSourceAndExternalId(String externalSource, String externalId);

	@Query("""
			select distinct p from ProductEntity p
			where exists (
			  select 1 from SaleItemEntity si, ArtifactPublicationEntity ap
			  where si.productId = p.id and si.parseAttemptId = ap.activeParseAttemptId
			)
			and (:q is null or lower(p.name) like lower(concat('%', cast(:q as string), '%'))
			     or p.externalId like concat('%', cast(:q as string), '%'))
			""")
	Page<ProductEntity> searchPublished(@Param("q") String q, Pageable pageable);

	@Query("""
			select p from ProductEntity p
			where p.id = :id
			  and exists (
			    select 1 from SaleItemEntity si, ArtifactPublicationEntity ap
			    where si.productId = p.id and si.parseAttemptId = ap.activeParseAttemptId
			  )
			""")
	Optional<ProductEntity> findPublishedById(@Param("id") UUID id);
}
