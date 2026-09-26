package com.spendsense.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all available FinancialTool components in the Spring context.
 */
@Component
public class ToolRegistry {

    private final Map<String, FinancialTool<?, ?>> tools = new ConcurrentHashMap<>();

    public ToolRegistry(List<FinancialTool<?, ?>> financialTools) {
        if (financialTools != null) {
            for (FinancialTool<?, ?> tool : financialTools) {
                tools.put(tool.getName(), tool);
            }
        }
    }

    public Optional<FinancialTool<?, ?>> getTool(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(tools.get(name.trim()));
    }

    public boolean hasTool(String name) {
        return name != null && tools.containsKey(name.trim());
    }

    public List<ToolDefinition> getAllToolDefinitions() {
        return tools.values().stream()
                .map(tool -> new ToolDefinition(
                        tool.getName(),
                        tool.getDescription(),
                        tool.getParameterSchema(),
                        tool.isMutating()
                ))
                .sorted(Comparator.comparing(ToolDefinition::name))
                .toList();
    }

    public List<FinancialTool<?, ?>> getAllTools() {
        return new ArrayList<>(tools.values());
    }
}
