package com.spendsense.categorization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorizationFeedbackRepository extends JpaRepository<CategorizationFeedback, UUID> {

    Page<CategorizationFeedback> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<CategorizationFeedback> findByCreatedAtBetweenOrderByCreatedAtAsc(Instant start, Instant end);

    long countByUserId(UUID userId);
}
