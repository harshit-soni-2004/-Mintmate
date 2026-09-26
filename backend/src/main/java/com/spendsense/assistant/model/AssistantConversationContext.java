package com.spendsense.assistant.model;

import java.util.Collections;
import java.util.List;

public record AssistantConversationContext(
        String systemPrompt,
        List<AssistantMessage> messages
) {
    public AssistantConversationContext {
        messages = messages != null ? Collections.unmodifiableList(messages) : Collections.emptyList();
    }
}
