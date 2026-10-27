package com.spendsense.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    List<Goal> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<Goal> findByIdAndUser_Id(UUID id, UUID userId);

    boolean existsByUser_IdAndNameIgnoreCase(UUID userId, String name);
}
