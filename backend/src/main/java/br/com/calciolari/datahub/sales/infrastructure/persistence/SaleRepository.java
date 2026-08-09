package br.com.calciolari.datahub.sales.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.calciolari.datahub.catalog.infrastructure.persistence.ProductEntity;

public interface SaleRepository extends JpaRepository<SaleEntity, UUID> {
	Optional<SaleEntity> findByExternalSourceAndExternalSaleId(String externalSource, String externalSaleId);

	@Query("""
			select distinct s from SaleEntity s, SaleItemEntity si, ArtifactPublicationEntity ap
			where si.saleId = s.id
			  and si.parseAttemptId = ap.activeParseAttemptId
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			order by s.occurredAt desc, s.externalSaleId desc
			""")
	Page<SaleEntity> searchPublished(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,
			Pageable pageable);

	@Query("""
			select s from SaleEntity s
			where s.id = :id
			  and exists (
			    select 1 from SaleItemEntity si, ArtifactPublicationEntity ap
			    where si.saleId = s.id and si.parseAttemptId = ap.activeParseAttemptId
			  )
			""")
	Optional<SaleEntity> findPublishedById(@Param("id") UUID id);

	@Query("""
			select coalesce(sum(si.total), 0) from SaleItemEntity si, ArtifactPublicationEntity ap, SaleEntity s
			where si.parseAttemptId = ap.activeParseAttemptId
			  and si.saleId = s.id
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			""")
	java.math.BigDecimal sumPublishedRevenue(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs);

	@Query("""
			select coalesce(sum(si.quantity), 0) from SaleItemEntity si, ArtifactPublicationEntity ap, SaleEntity s
			where si.parseAttemptId = ap.activeParseAttemptId
			  and si.saleId = s.id
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			""")
	java.math.BigDecimal sumPublishedQuantity(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs);

	@Query("""
			select count(distinct s.id) from SaleEntity s, SaleItemEntity si, ArtifactPublicationEntity ap
			where si.saleId = s.id and si.parseAttemptId = ap.activeParseAttemptId
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			""")
	long countPublishedSales(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs);

	@Query("""
			select count(si) from SaleItemEntity si, ArtifactPublicationEntity ap, SaleEntity s
			where si.parseAttemptId = ap.activeParseAttemptId
			  and si.saleId = s.id
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			""")
	long countPublishedItems(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs);

	@Query("""
			select min(s.occurredAt) from SaleEntity s, SaleItemEntity si, ArtifactPublicationEntity ap
			where si.saleId = s.id and si.parseAttemptId = ap.activeParseAttemptId
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			""")
	LocalDateTime minPublishedOccurredAt(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs);

	@Query("""
			select max(s.occurredAt) from SaleEntity s, SaleItemEntity si, ArtifactPublicationEntity ap
			where si.saleId = s.id and si.parseAttemptId = ap.activeParseAttemptId
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			""")
	LocalDateTime maxPublishedOccurredAt(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs);

	@Query("""
			select s.occurredAt, si.quantity, si.total from SaleItemEntity si, SaleEntity s, ArtifactPublicationEntity ap
			where si.saleId = s.id and si.parseAttemptId = ap.activeParseAttemptId
			  and s.occurredAt is not null
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			""")
	List<Object[]> publishedMovementFacts(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs);

	@Query("""
			select p.id, p.name, p.externalId, sum(si.quantity), sum(si.total)
			from SaleItemEntity si, SaleEntity s, ArtifactPublicationEntity ap, ProductEntity p
			where si.saleId = s.id and si.parseAttemptId = ap.activeParseAttemptId
			  and p.id = si.productId
			  and (:productId is null or si.productId = :productId)
			  and s.occurredAt >= :fromTs
			  and s.occurredAt <= :toTs
			group by p.id, p.name, p.externalId
			order by sum(si.total) desc
			""")
	List<Object[]> topPublishedProducts(
			@Param("productId") UUID productId,
			@Param("fromTs") LocalDateTime fromTs,
			@Param("toTs") LocalDateTime toTs,
			Pageable pageable);
}
