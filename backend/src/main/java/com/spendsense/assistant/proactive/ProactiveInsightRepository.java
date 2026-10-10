package com.spendsense.assistant.proactive;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProactiveInsightRepository extends JpaRepository<ProactiveInsightEntity, UUID> {

    List<ProactiveInsightEntity> findByUser_IdAndDismissedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId, Instant now);

    boolean existsByUser_IdAndDeduplicationKeyAndCreatedAtAfter(
            UUID userId, String deduplicationKey, Instant after);

    Optional<ProactiveInsightEntity> findByIdAndUser_Id(UUID id, UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProactiveInsightEntity i SET i.dismissed = true WHERE i.id = :id AND i.user.id = :userId")
    int dismissInsight(@Param("id") UUID id, @Param("userId") UUID userId);
}
