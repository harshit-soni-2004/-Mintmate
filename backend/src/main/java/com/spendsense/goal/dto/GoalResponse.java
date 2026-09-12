package com.spendsense.goal.dto;

import com.spendsense.goal.Goal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String name,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal remainingAmount,
        double progressPercentage,
        boolean isCompleted,
        LocalDate targetDate,
        Instant createdAt,
        Instant updatedAt
) {
    public static GoalResponse of(Goal goal, BigDecimal remainingAmount, double progressPercentage) {
        boolean completed = goal.getCurrentAmount() != null &&
                goal.getTargetAmount() != null &&
                goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0;

        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                remainingAmount,
                progressPercentage,
                completed,
                goal.getTargetDate(),
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }
}
