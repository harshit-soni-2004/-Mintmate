package com.spendsense.account;

import com.spendsense.document.DocumentAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    List<FinancialAccount> findByUserIdAndActiveTrueOrderByCreatedAtDesc(UUID userId);

    List<FinancialAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<FinancialAccount> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT a FROM FinancialAccount a WHERE a.user.id = :userId " +
           "AND LOWER(a.institutionName) = LOWER(:institutionName) " +
           "AND a.accountType = :accountType " +
           "AND (:last4 IS NULL OR a.accountNumberLast4 = :last4)")
    Optional<FinancialAccount> findMatchingAccount(
            @Param("userId") UUID userId,
            @Param("institutionName") String institutionName,
            @Param("accountType") DocumentAccountType accountType,
            @Param("last4") String last4
    );
}
