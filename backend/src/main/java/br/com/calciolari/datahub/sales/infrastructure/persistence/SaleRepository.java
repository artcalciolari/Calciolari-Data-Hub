package br.com.calciolari.datahub.sales.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<SaleEntity, UUID> {
	Optional<SaleEntity> findByExternalSourceAndExternalSaleId(String externalSource, String externalSaleId);
}
