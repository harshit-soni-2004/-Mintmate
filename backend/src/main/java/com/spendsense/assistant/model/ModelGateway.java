package com.spendsense.assistant.model;

import com.spendsense.assistant.tool.ToolDefinition;

import java.util.List;

/**
 * Provider-agnostic gateway for LLM completions and function calling.
 */
public interface ModelGateway {

    /**
     * Generates an assistant response, which may be direct text or structured tool call requests.
     */
    AssistantResponse generateResponse(AssistantConversationContext context, List<ToolDefinition> tools);

    /**
     * Provider identifier, e.g. "gemini", "mock", "claude", "openai".
     */
    String getProviderName();
}
