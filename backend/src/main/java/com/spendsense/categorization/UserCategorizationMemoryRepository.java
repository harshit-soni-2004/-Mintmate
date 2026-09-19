package com.spendsense.categorization;

import com.spendsense.transaction.TransactionCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserCategorizationMemoryRepository extends JpaRepository<UserCategorizationMemory, UUID> {

    @Query("SELECT m FROM UserCategorizationMemory m WHERE m.user.id = :userId AND m.normalizedMerchant = :normalizedMerchant ORDER BY m.usageCount DESC, m.lastUsedAt DESC")
    List<UserCategorizationMemory> findAllByUserIdAndNormalizedMerchant(
            @Param("userId") UUID userId,
            @Param("normalizedMerchant") String normalizedMerchant
    );

    Optional<UserCategorizationMemory> findByUserIdAndNormalizedMerchantAndCategory(
            UUID userId,
            String normalizedMerchant,
            TransactionCategory category
    );

    long countByUserId(UUID userId);
}
