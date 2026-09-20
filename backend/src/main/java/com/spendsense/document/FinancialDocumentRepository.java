package com.spendsense.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialDocumentRepository extends JpaRepository<FinancialDocument, UUID>, JpaSpecificationExecutor<FinancialDocument> {

    Optional<FinancialDocument> findByIdAndUserId(UUID id, UUID userId);

    Optional<FinancialDocument> findByUserIdAndContentHash(UUID userId, String contentHash);

    boolean existsByUserIdAndContentHash(UUID userId, String contentHash);

    List<FinancialDocument> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
