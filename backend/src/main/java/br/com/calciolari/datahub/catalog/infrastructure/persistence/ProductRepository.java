package br.com.calciolari.datahub.catalog.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
	Optional<ProductEntity> findByExternalSourceAndExternalId(String externalSource, String externalId);
}
