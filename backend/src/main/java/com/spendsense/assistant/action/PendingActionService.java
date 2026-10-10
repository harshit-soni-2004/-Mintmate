package com.spendsense.assistant.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.dto.PendingActionDto;
import com.spendsense.assistant.dto.PendingActionResultDto;
import com.spendsense.assistant.telemetry.AssistantTelemetryService;
import com.spendsense.assistant.tool.FinancialTool;
import com.spendsense.assistant.tool.ToolRegistry;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class PendingActionService {

    private static final Logger log = LoggerFactory.getLogger(PendingActionService.class);
    private static final Duration DEFAULT_EXPIRATION = Duration.ofMinutes(15);

    private final PendingActionRepository pendingActionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNewTxTemplate;
    private final TransactionTemplate standardTxTemplate;
    private final AssistantTelemetryService telemetryService;

    public PendingActionService(
            PendingActionRepository pendingActionRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper
    ) {
        this(pendingActionRepository, userRepository, currentUser, toolRegistry, objectMapper, null, null);
    }

    public PendingActionService(
            PendingActionRepository pendingActionRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this(pendingActionRepository, userRepository, currentUser, toolRegistry, objectMapper, transactionManager, null);
    }

    @Autowired
    public PendingActionService(
            PendingActionRepository pendingActionRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper,
            @Autowired(required = false) PlatformTransactionManager transactionManager,
            @Autowired(required = false) AssistantTelemetryService telemetryService
    ) {
        this.pendingActionRepository = pendingActionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.telemetryService = telemetryService;
        if (transactionManager != null) {
            this.requiresNewTxTemplate = new TransactionTemplate(transactionManager);
            this.requiresNewTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.standardTxTemplate = new TransactionTemplate(transactionManager);
        } else {
            this.requiresNewTxTemplate = null;
            this.standardTxTemplate = null;
        }
    }

    /**
     * Persists a validated mutation as a PendingAction requiring explicit user confirmation.
     */
    @Transactional
    public PendingActionDto createPendingAction(String toolName, Object validatedInput, String summaryText) {
        SpendSenseUserPrincipal principal = currentUser.require();
        User user = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        String parametersJson;
        try {
            parametersJson = objectMapper.writeValueAsString(validatedInput);
        } catch (Exception e) {
            log.error("Failed to serialize pending action parameters: {}", e.getMessage());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_ERROR", "Failed to serialize parameters");
        }

        Instant expiresAt = Instant.now().plus(DEFAULT_EXPIRATION);
        PendingAction pendingAction = new PendingAction(user, toolName, parametersJson, summaryText, expiresAt);
        PendingAction saved = pendingActionRepository.save(pendingAction);

        log.info("Created PendingAction id={} tool={} for user={}", saved.getId(), toolName, user.getId());
        return new PendingActionDto(saved.getId(), toolName, summaryText, expiresAt);
    }

    /**
     * Atomically confirms and executes a pending action.
     * Guaranteed replay protection: exactly one attempt transitions PENDING -> CONFIRMED -> EXECUTED.
     */
    public PendingActionResultDto confirmAndExecute(UUID actionId) {
        long startTime = System.currentTimeMillis();
        SpendSenseUserPrincipal principal = currentUser.require();
        UUID userId = principal.getUserId();

        PendingAction action = pendingActionRepository.findByIdAndUser_Id(actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTION_NOT_FOUND", "Pending action not found or not owned by user"));

        Instant now = Instant.now();

        if (action.isExpired()) {
            action.setStatus(PendingActionStatus.EXPIRED);
            pendingActionRepository.transitionStatus(actionId, userId, PendingActionStatus.PENDING, PendingActionStatus.EXPIRED);
            if (telemetryService != null) {
                telemetryService.logActionConfirmed(userId, action.getToolName(), System.currentTimeMillis() - startTime, false);
            }
            throw new ApiException(HttpStatus.GONE, "ACTION_EXPIRED", "This action has expired. Please request the change again.");
        }

        if (action.getStatus() != PendingActionStatus.PENDING) {
            if (telemetryService != null) {
                telemetryService.logActionConfirmed(userId, action.getToolName(), System.currentTimeMillis() - startTime, false);
            }
            throw new ApiException(HttpStatus.CONFLICT, "ACTION_NOT_PENDING", "Action is not pending confirmation. Current status: " + action.getStatus());
        }

        // 1. Atomically lock state to CONFIRMED in an isolated transaction to prevent concurrent replays
        executeInRequiresNew(() -> {
            int rowsUpdated = pendingActionRepository.transitionToConfirmed(actionId, userId, PendingActionStatus.PENDING, PendingActionStatus.CONFIRMED, now);
            if (rowsUpdated == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "REPLAY_DETECTED", "Action has already been confirmed or processed.");
            }
        });

        // 2. Dispatch execution to the mutation tool within transactional boundary
        FinancialTool<?, ?> tool = toolRegistry.getTool(action.getToolName())
                .orElseThrow(() -> {
                    markFailed(actionId, userId, "Mutation tool not found: " + action.getToolName());
                    if (telemetryService != null) {
                        telemetryService.logActionConfirmed(userId, action.getToolName(), System.currentTimeMillis() - startTime, false);
                    }
                    return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TOOL_NOT_FOUND", "Mutation tool not found: " + action.getToolName());
                });

        Object mutationResult;
        Instant executedAt;
        try {
            mutationResult = executeInStandardTx(() -> {
                Object res = tool.executeConfirmedMutation(action.getParametersJson(), principal);
                Instant execTime = Instant.now();
                pendingActionRepository.transitionToExecuted(actionId, userId, PendingActionStatus.CONFIRMED, PendingActionStatus.EXECUTED, execTime);
                return res;
            });
            executedAt = Instant.now();
            log.info("Successfully executed PendingAction id={} for user={}", actionId, userId);
            if (telemetryService != null) {
                telemetryService.logActionConfirmed(userId, action.getToolName(), System.currentTimeMillis() - startTime, true);
            }
        } catch (ApiException e) {
            log.error("Failed executing confirmed mutation action {}: {}", actionId, e.getMessage());
            markFailed(actionId, userId, e.getMessage());
            if (telemetryService != null) {
                telemetryService.logActionConfirmed(userId, action.getToolName(), System.currentTimeMillis() - startTime, false);
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed executing confirmed mutation action {}: {}", actionId, e.getMessage(), e);
            markFailed(actionId, userId, e.getMessage());
            if (telemetryService != null) {
                telemetryService.logActionConfirmed(userId, action.getToolName(), System.currentTimeMillis() - startTime, false);
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EXECUTION_FAILED", "Failed executing confirmed action: " + e.getMessage());
        }

        return new PendingActionResultDto(
                actionId,
                action.getToolName(),
                PendingActionStatus.EXECUTED,
                action.getSummaryText(),
                mutationResult,
                executedAt
        );
    }

    private void markFailed(UUID actionId, UUID userId, String failureInfo) {
        try {
            executeInRequiresNew(() -> {
                String safeInfo = failureInfo != null && failureInfo.length() > 900
                        ? failureInfo.substring(0, 900)
                        : failureInfo;
                pendingActionRepository.transitionToFailed(actionId, userId, PendingActionStatus.FAILED, safeInfo);
            });
        } catch (Exception ex) {
            log.error("Failed recording FAILED status for action {}: {}", actionId, ex.getMessage());
        }
    }

    private void executeInRequiresNew(Runnable action) {
        if (requiresNewTxTemplate != null) {
            requiresNewTxTemplate.executeWithoutResult(status -> action.run());
        } else {
            action.run();
        }
    }

    private <T> T executeInStandardTx(Supplier<T> action) {
        if (standardTxTemplate != null) {
            return standardTxTemplate.execute(status -> action.get());
        } else {
            return action.get();
        }
    }

    /**
     * Explicitly cancels a pending action.
     */
    @Transactional
    public void cancelAction(UUID actionId) {
        SpendSenseUserPrincipal principal = currentUser.require();
        UUID userId = principal.getUserId();

        PendingAction action = pendingActionRepository.findByIdAndUser_Id(actionId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACTION_NOT_FOUND", "Pending action not found or not owned by user"));

        if (action.getStatus() != PendingActionStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CANCEL", "Cannot cancel action in status: " + action.getStatus());
        }

        int rows = pendingActionRepository.transitionStatus(actionId, userId, PendingActionStatus.PENDING, PendingActionStatus.CANCELLED);
        if (rows == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTION_ALREADY_PROCESSED", "Action could not be cancelled.");
        }
        log.info("Cancelled PendingAction id={} for user={}", actionId, userId);
        if (telemetryService != null) {
            telemetryService.logActionCancelled(userId, action.getToolName());
        }
    }
}
