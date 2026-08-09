package br.com.calciolari.datahub.sales.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleItemRepository extends JpaRepository<SaleItemEntity, UUID> {
	List<SaleItemEntity> findByParseAttemptId(UUID parseAttemptId);

	long countByParseAttemptId(UUID parseAttemptId);

	@Query("""
			select si from SaleItemEntity si, ArtifactPublicationEntity ap
			where si.saleId = :saleId
			  and si.parseAttemptId = ap.activeParseAttemptId
			order by si.sourceRecordIndex asc
			""")
	List<SaleItemEntity> findPublishedBySaleId(@Param("saleId") UUID saleId);

	@Query("""
			select coalesce(sum(si.total), 0) from SaleItemEntity si, ArtifactPublicationEntity ap
			where si.saleId = :saleId and si.parseAttemptId = ap.activeParseAttemptId
			""")
	java.math.BigDecimal sumPublishedTotalForSale(@Param("saleId") UUID saleId);
}
