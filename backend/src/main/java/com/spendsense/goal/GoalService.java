package com.spendsense.goal;

import com.spendsense.common.exception.ApiException;
import com.spendsense.goal.dto.GoalContributionRequest;
import com.spendsense.goal.dto.GoalRequest;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public GoalService(GoalRepository goalRepository, UserRepository userRepository, CurrentUser currentUser) {
        this.goalRepository = goalRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public GoalResponse create(GoalRequest request) {
        validateGoalRequest(request);
        User user = requireUser();

        BigDecimal initialCurrent = request.currentAmount() != null ? request.currentAmount() : BigDecimal.ZERO;
        Goal goal = new Goal(
                user,
                request.name().trim(),
                request.targetAmount(),
                initialCurrent,
                request.targetDate()
        );

        Goal saved = goalRepository.save(goal);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list() {
        UUID userId = currentUser.require().getUserId();
        return goalRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(UUID id) {
        Goal goal = findOwned(id);
        return toResponse(goal);
    }

    @Transactional
    public GoalResponse update(UUID id, GoalRequest request) {
        validateGoalRequest(request);
        Goal goal = findOwned(id);

        BigDecimal current = request.currentAmount() != null ? request.currentAmount() : goal.getCurrentAmount();
        goal.update(request.name().trim(), request.targetAmount(), current, request.targetDate());

        Goal saved = goalRepository.save(goal);
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Goal goal = findOwned(id);
        goalRepository.delete(goal);
    }

    @Transactional
    public GoalResponse addContribution(UUID id, GoalContributionRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONTRIBUTION", "Contribution amount must be greater than zero");
        }

        Goal goal = findOwned(id);
        goal.addContribution(request.amount());
        Goal saved = goalRepository.save(goal);
        return toResponse(saved);
    }

    public GoalResponse toResponse(Goal goal) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal current = goal.getCurrentAmount();
        BigDecimal remaining = target.subtract(current).max(BigDecimal.ZERO);

        double percentage = target.compareTo(BigDecimal.ZERO) > 0
                ? current.divide(target, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue()
                : 0.0;

        return GoalResponse.of(goal, remaining, percentage);
    }

    private Goal findOwned(UUID id) {
        UUID userId = currentUser.require().getUserId();
        return goalRepository.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOAL_NOT_FOUND", "Goal not found"));
    }

    private User requireUser() {
        UUID userId = currentUser.require().getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private void validateGoalRequest(GoalRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GOAL_NAME", "Goal name is required");
        }
        if (request.targetAmount() == null || request.targetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_AMOUNT", "Target amount must be greater than zero");
        }
        if (request.currentAmount() != null && request.currentAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURRENT_AMOUNT", "Current amount cannot be negative");
        }
    }
}
