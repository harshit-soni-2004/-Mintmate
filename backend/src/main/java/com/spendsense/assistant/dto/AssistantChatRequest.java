package com.spendsense.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AssistantChatRequest(
        @NotBlank(message = "Message cannot be blank")
        @Size(max = 2000, message = "Message must not exceed 2000 characters")
        String message,

        @Size(max = 30, message = "Conversation history must not exceed 30 messages")
        List<@Valid ChatMessageDto> conversationHistory
) {}
