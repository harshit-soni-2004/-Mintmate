package com.spendsense.assistant.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.dto.PendingActionDto;
import com.spendsense.assistant.dto.PendingActionResultDto;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolRegistry;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingActionServiceTest {

    @Mock private PendingActionRepository pendingActionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;
    @Mock private ToolRegistry toolRegistry;
    @Mock private FinancialTool<?, ?> mutationTool;

    private PendingActionService service;
    private User testUser;
    private SpendSenseUserPrincipal principal;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        testUser = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        principal = new SpendSenseUserPrincipal(testUser);
        when(currentUser.require()).thenReturn(principal);

        service = new PendingActionService(
                pendingActionRepository,
                userRepository,
                currentUser,
                toolRegistry,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should create PendingAction with 15-minute expiration and PENDING status")
    void shouldCreatePendingAction() {
        when(userRepository.findById(principal.getUserId())).thenReturn(Optional.of(testUser));
        when(pendingActionRepository.save(any(PendingAction.class))).thenAnswer(i -> i.getArgument(0));

        PendingActionDto dto = service.createPendingAction(
                "update_transaction_category",
                Map.of("category", "FOOD"),
                "Change category to Food"
        );

        assertThat(dto).isNotNull();
        assertThat(dto.actionType()).isEqualTo("update_transaction_category");
        assertThat(dto.summary()).isEqualTo("Change category to Food");
        assertThat(dto.expiresAt()).isAfter(Instant.now().plus(14, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Replay Protection: first confirmation executes, second attempt fails safely")
    void replayProtection_secondAttemptFails() {
        UUID actionId = UUID.randomUUID();
        PendingAction action = new PendingAction(
                testUser,
                "update_transaction_category",
                "{\"category\":\"FOOD\"}",
                "Change to Food",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(action));
        when(toolRegistry.getTool("update_transaction_category")).thenReturn(Optional.of(mutationTool));
        when(mutationTool.executeConfirmedMutation(any(), any())).thenReturn("mutation-success");

        // First attempt succeeds: transitionToConfirmed returns 1 row updated
        when(pendingActionRepository.transitionToConfirmed(eq(actionId), eq(principal.getUserId()), eq(PendingActionStatus.PENDING), eq(PendingActionStatus.CONFIRMED), any()))
                .thenReturn(1);

        PendingActionResultDto result = service.confirmAndExecute(actionId);
        assertThat(result.status()).isEqualTo(PendingActionStatus.EXECUTED);
        assertThat(result.result()).isEqualTo("mutation-success");

        // Second attempt fails: action is now CONFIRMED or transitionToConfirmed returns 0
        action.setStatus(PendingActionStatus.CONFIRMED);
        assertThatThrownBy(() -> service.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("Expired PendingAction must be rejected with 410 GONE")
    void expiredAction_failsSafely() {
        UUID actionId = UUID.randomUUID();
        PendingAction expiredAction = new PendingAction(
                testUser,
                "update_transaction_category",
                "{}",
                "Change to Food",
                Instant.now().minus(5, ChronoUnit.MINUTES) // Already expired
        );

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(expiredAction));

        assertThatThrownBy(() -> service.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.GONE);

        assertThat(expiredAction.getStatus()).isEqualTo(PendingActionStatus.EXPIRED);
    }

    @Test
    @DisplayName("IDOR Isolation: User cannot confirm another user's action")
    void idorIsolation_cannotConfirmOtherUsersAction() {
        UUID actionId = UUID.randomUUID();
        // Repository returns empty because user_id does not match
        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Cancelled PendingAction transitions to CANCELLED")
    void cancelAction_transitionsToCancelled() {
        UUID actionId = UUID.randomUUID();
        PendingAction action = new PendingAction(
                testUser,
                "update_transaction_category",
                "{}",
                "Change to Food",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(action));
        when(pendingActionRepository.transitionStatus(actionId, principal.getUserId(), PendingActionStatus.PENDING, PendingActionStatus.CANCELLED))
                .thenReturn(1);

        service.cancelAction(actionId);

        verify(pendingActionRepository).transitionStatus(actionId, principal.getUserId(), PendingActionStatus.PENDING, PendingActionStatus.CANCELLED);
    }

    @Test
    @DisplayName("Mutation Failure: when confirmed execution fails, status transitions to FAILED with failure info")
    void mutationFailure_transitionsToFailed() {
        UUID actionId = UUID.randomUUID();
        PendingAction action = new PendingAction(
                testUser,
                "create_or_update_goal",
                "{\"name\":\"Emergency\"}",
                "Create savings goal",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(action));
        when(pendingActionRepository.transitionToConfirmed(eq(actionId), eq(principal.getUserId()), eq(PendingActionStatus.PENDING), eq(PendingActionStatus.CONFIRMED), any()))
                .thenReturn(1);
        when(toolRegistry.getTool("create_or_update_goal")).thenReturn(Optional.of(mutationTool));
        when(mutationTool.executeConfirmedMutation(any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_DATE", "Goal target date must be in the future"));

        assertThatThrownBy(() -> service.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);

        verify(pendingActionRepository).transitionToFailed(
                eq(actionId), eq(principal.getUserId()), eq(PendingActionStatus.FAILED), eq("Goal target date must be in the future")
        );
    }

    @Test
    @DisplayName("State Machine: CANCELLED action cannot be confirmed")
    void cancelledAction_cannotBeConfirmed() {
        UUID actionId = UUID.randomUUID();
        PendingAction action = new PendingAction(
                testUser,
                "update_transaction_category",
                "{}",
                "Change to Food",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );
        action.setStatus(PendingActionStatus.CANCELLED);

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("State Machine: EXECUTED action cannot be re-executed")
    void executedAction_cannotBeReConfirmed() {
        UUID actionId = UUID.randomUUID();
        PendingAction action = new PendingAction(
                testUser,
                "update_transaction_category",
                "{}",
                "Change to Food",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );
        action.setStatus(PendingActionStatus.EXECUTED);

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.confirmAndExecute(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("State Machine: Non-pending action cannot be cancelled")
    void nonPendingAction_cannotBeCancelled() {
        UUID actionId = UUID.randomUUID();
        PendingAction action = new PendingAction(
                testUser,
                "update_transaction_category",
                "{}",
                "Change to Food",
                Instant.now().plus(10, ChronoUnit.MINUTES)
        );
        action.setStatus(PendingActionStatus.EXECUTED);

        when(pendingActionRepository.findByIdAndUser_Id(actionId, principal.getUserId()))
                .thenReturn(Optional.of(action));

        assertThatThrownBy(() -> service.cancelAction(actionId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }
}
