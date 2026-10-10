package com.spendsense.assistant.proactive;

import com.spendsense.analytics.InsightSeverity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProactiveInsightDto(
        UUID id,
        ProactiveInsightType type,
        InsightSeverity severity,
        String title,
        String summary,
        Map<String, Object> supportingFacts,
        String actionRecommendation,
        String deduplicationKey,
        Instant createdAt,
        Instant expiresAt
) {
}
