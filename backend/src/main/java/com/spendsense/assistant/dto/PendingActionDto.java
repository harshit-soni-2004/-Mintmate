package com.spendsense.assistant.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record PendingActionDto(
        UUID actionId,
        String actionType,
        String summary,
        Map<String, Object> parameters,
        Instant expiresAt
) {
    public PendingActionDto(UUID actionId, String actionType, String summary, Instant expiresAt) {
        this(actionId, actionType, summary, Collections.emptyMap(), expiresAt);
    }
}
