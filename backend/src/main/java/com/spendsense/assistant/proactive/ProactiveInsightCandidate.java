package com.spendsense.assistant.proactive;

import com.spendsense.analytics.InsightSeverity;

import java.util.Map;

public record ProactiveInsightCandidate(
        ProactiveInsightType type,
        InsightSeverity severity,
        String title,
        String summary,
        Map<String, Object> supportingFacts,
        String actionRecommendation,
        String deduplicationKey,
        double relevanceScore
) {
}
