package com.spendsense.assistant.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lightweight telemetry and observability service for SpendSense AI Assistant.
 * Strictly avoids logging sensitive transaction amounts, raw prompt contents, API keys, or PII.
 */
@Service
public class AssistantTelemetryService {

    private static final Logger log = LoggerFactory.getLogger("AI_TELEMETRY");

    public record ChatTelemetryContext(
            String requestId,
            String userHash,
            Instant startedAt,
            long requestStartTimeNs
    ) {
        public static ChatTelemetryContext create(UUID userId) {
            String userHash = hashUser(userId);
            String reqId = "REQ-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            return new ChatTelemetryContext(reqId, userHash, Instant.now(), System.nanoTime());
        }
    }

    public static String hashUser(UUID userId) {
        if (userId == null) {
            return "anon";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userId.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder("usr_");
            for (int i = 0; i < 4; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "usr_" + userId.toString().substring(0, 8);
        }
    }

    public void logModelCall(String requestId, String provider, long durationMs, boolean success, int toolCallsCount) {
        log.info("AI_MODEL_CALL: reqId={} provider={} durationMs={} success={} toolCallsCount={}",
                requestId, provider, durationMs, success, toolCallsCount);
    }

    public void logToolExecution(String requestId, String toolName, long durationMs, boolean success, boolean isMutation) {
        log.info("AI_TOOL_EXECUTION: reqId={} tool={} durationMs={} success={} isMutation={}",
                requestId, toolName, durationMs, success, isMutation);
    }

    public void logChatCompleted(ChatTelemetryContext ctx, long totalDurationMs, int turnsCount, List<String> toolsInvoked, boolean actionProposed) {
        String reqId = ctx != null ? ctx.requestId() : "none";
        String user = ctx != null ? ctx.userHash() : "anon";
        log.info("AI_CHAT_COMPLETED: reqId={} user={} totalDurationMs={} turns={} toolsCount={} tools={} actionProposed={}",
                reqId, user, totalDurationMs, turnsCount, toolsInvoked.size(), toolsInvoked, actionProposed);
    }

    public void logActionConfirmed(UUID userId, String toolName, long durationMs, boolean success) {
        log.info("AI_ACTION_CONFIRMED: user={} tool={} durationMs={} success={}",
                hashUser(userId), toolName, durationMs, success);
    }

    public void logActionCancelled(UUID userId, String toolName) {
        log.info("AI_ACTION_CANCELLED: user={} tool={}",
                hashUser(userId), toolName);
    }

    public void logProactiveInsightGeneration(UUID userId, int insightsDetected, int insightsSaved) {
        log.info("AI_PROACTIVE_INSIGHTS: user={} detected={} saved={}",
                hashUser(userId), insightsDetected, insightsSaved);
    }
}
