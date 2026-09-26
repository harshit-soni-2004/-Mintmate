package com.spendsense.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageDto(
        @NotBlank(message = "Role is required")
        @Size(max = 32, message = "Role must not exceed 32 characters")
        String role,

        @NotBlank(message = "Content is required")
        @Size(max = 4000, message = "Content must not exceed 4000 characters")
        String content
) {}
