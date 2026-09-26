package com.spendsense.assistant.dto;

import com.spendsense.assistant.action.PendingActionStatus;

import java.time.Instant;
import java.util.UUID;

public record PendingActionResultDto(
        UUID actionId,
        String toolName,
        PendingActionStatus status,
        String summaryText,
        Object result,
        Instant executedAt
) {}
