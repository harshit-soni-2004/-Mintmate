package com.spendsense.ingestion.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatementSettlementRepository extends JpaRepository<StatementSettlement, UUID> {

    List<StatementSettlement> findByUserIdAndStatus(UUID userId, SettlementStatus status);

    List<StatementSettlement> findByUserIdAndStatusAndAmount(UUID userId, SettlementStatus status, BigDecimal amount);

    List<StatementSettlement> findByDocumentId(UUID documentId);

    @org.springframework.data.jpa.repository.Query("SELECT s FROM StatementSettlement s WHERE s.document.id = :documentId AND s.user.id = :userId")
    List<StatementSettlement> findByDocumentIdAndUserId(
            @org.springframework.data.repository.query.Param("documentId") UUID documentId,
            @org.springframework.data.repository.query.Param("userId") UUID userId);

    Optional<StatementSettlement> findByDocumentIdAndRowIndex(UUID documentId, int rowIndex);
}
