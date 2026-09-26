package com.spendsense.assistant.model;

import java.util.List;

public record AssistantMessage(
        AssistantMessageRole role,
        String content,
        String toolCallId,
        String toolName,
        List<ToolCallRequest> toolCalls
) {
    public static AssistantMessage system(String content) {
        return new AssistantMessage(AssistantMessageRole.SYSTEM, content, null, null, null);
    }

    public static AssistantMessage user(String content) {
        return new AssistantMessage(AssistantMessageRole.USER, content, null, null, null);
    }

    public static AssistantMessage assistant(String content) {
        return new AssistantMessage(AssistantMessageRole.ASSISTANT, content, null, null, null);
    }

    public static AssistantMessage assistantWithToolCalls(String content, List<ToolCallRequest> toolCalls) {
        return new AssistantMessage(AssistantMessageRole.ASSISTANT, content, null, null, toolCalls);
    }

    public static AssistantMessage tool(String toolCallId, String toolName, String content) {
        return new AssistantMessage(AssistantMessageRole.TOOL, content, toolCallId, toolName, null);
    }
}
