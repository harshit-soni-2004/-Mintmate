package com.spendsense.assistant.model;

import java.util.Collections;
import java.util.List;

public record AssistantResponse(
        String content,
        List<ToolCallRequest> toolCalls
) {
    public static AssistantResponse text(String content) {
        return new AssistantResponse(content, Collections.emptyList());
    }

    public static AssistantResponse ofToolCalls(List<ToolCallRequest> toolCalls) {
        return new AssistantResponse(null, toolCalls);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
