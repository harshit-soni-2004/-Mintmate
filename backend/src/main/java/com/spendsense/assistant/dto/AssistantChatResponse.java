package com.spendsense.assistant.dto;

import java.util.List;

public record AssistantChatResponse(
        String answer,
        List<ToolCallSummaryDto> executedTools,
        boolean requiresConfirmation,
        PendingActionDto pendingAction
) {
    public static AssistantChatResponse text(String answer, List<ToolCallSummaryDto> executedTools) {
        return new AssistantChatResponse(answer, executedTools, false, null);
    }

    public static AssistantChatResponse confirmationRequired(String answer, List<ToolCallSummaryDto> executedTools, PendingActionDto pendingAction) {
        return new AssistantChatResponse(answer, executedTools, true, pendingAction);
    }
}
