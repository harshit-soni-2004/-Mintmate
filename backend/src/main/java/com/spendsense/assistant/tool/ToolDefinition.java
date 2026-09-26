package com.spendsense.assistant.tool;

import java.util.Map;

/**
 * Definition of an available tool exported to the model provider gateway.
 */
public record ToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters,
        boolean mutating
) {}
