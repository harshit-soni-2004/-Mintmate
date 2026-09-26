package com.spendsense.assistant.model;

import java.util.Map;

public record ToolCallRequest(
        String id,
        String name,
        Map<String, Object> arguments
) {}
