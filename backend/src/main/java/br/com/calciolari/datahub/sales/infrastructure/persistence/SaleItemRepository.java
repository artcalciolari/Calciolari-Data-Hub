package br.com.calciolari.datahub.sales.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleItemRepository extends JpaRepository<SaleItemEntity, UUID> {
	List<SaleItemEntity> findByParseAttemptId(UUID parseAttemptId);

	long countByParseAttemptId(UUID parseAttemptId);
}
