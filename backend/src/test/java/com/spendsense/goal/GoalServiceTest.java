package com.spendsense.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.common.exception.ApiException;
import com.spendsense.goal.dto.GoalContributionRequest;
import com.spendsense.goal.dto.GoalRequest;
import com.spendsense.goal.dto.GoalResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock private GoalRepository goalRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;

    private User user;
    private GoalService goalService;

    @BeforeEach
    void setUp() {
        user = new User("rahul@example.com", "hash", "Rahul Patel", "Asia/Kolkata");
        lenient().when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        goalService = new GoalService(goalRepository, userRepository, currentUser);
    }

    @Test
    void createsGoalSuccessfully() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(goalRepository.save(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

        GoalRequest req = new GoalRequest(
                "Goa Trip",
                new BigDecimal("30000.00"),
                new BigDecimal("10000.00"),
                LocalDate.of(2026, 12, 31)
        );

        GoalResponse res = goalService.create(req);

        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Goa Trip");
        assertThat(res.targetAmount()).isEqualByComparingTo("30000.00");
        assertThat(res.currentAmount()).isEqualByComparingTo("10000.00");
        assertThat(res.remainingAmount()).isEqualByComparingTo("20000.00");
        assertThat(res.progressPercentage()).isEqualTo(33.33);
        assertThat(res.isCompleted()).isFalse();
    }

    @Test
    void addsContributionSuccessfully() {
        Goal goal = new Goal(user, "Emergency Fund", new BigDecimal("100000.00"),
                new BigDecimal("40000.00"), LocalDate.of(2027, 3, 31));
        when(goalRepository.findByIdAndUser_Id(any(), any())).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(i -> i.getArgument(0));

        GoalResponse res = goalService.addContribution(goal.getId(), new GoalContributionRequest(new BigDecimal("15000.00")));

        assertThat(res.currentAmount()).isEqualByComparingTo("55000.00");
        assertThat(res.remainingAmount()).isEqualByComparingTo("45000.00");
        assertThat(res.progressPercentage()).isEqualTo(55.0);
    }

    @Test
    void rejectsZeroOrNegativeContribution() {
        assertThatThrownBy(() -> goalService.addContribution(UUID.randomUUID(), new GoalContributionRequest(BigDecimal.ZERO)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_CONTRIBUTION");

        assertThatThrownBy(() -> goalService.addContribution(UUID.randomUUID(), new GoalContributionRequest(new BigDecimal("-100.00"))))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INVALID_CONTRIBUTION");
    }

    @Test
    void userCannotAccessAnotherUsersGoal() {
        when(goalRepository.findByIdAndUser_Id(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.get(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("GOAL_NOT_FOUND");
    }
}
