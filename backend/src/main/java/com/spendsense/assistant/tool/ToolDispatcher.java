package com.spendsense.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates and dispatches tool execution requests from the assistant orchestrator.
 */
@Component
public class ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcher.class);

    private final ToolRegistry toolRegistry;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public ToolDispatcher(ToolRegistry toolRegistry, CurrentUser currentUser, ObjectMapper objectMapper, Validator validator) {
        this.toolRegistry = toolRegistry;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    /**
     * Executes the requested tool by name with the given argument map or JSON.
     * Enforces that user identity is derived strictly from CurrentUser.
     */
    public ToolExecutionResult<?> dispatch(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.isBlank()) {
            return ToolExecutionResult.failure("Tool name cannot be blank");
        }

        FinancialTool<?, ?> tool = toolRegistry.getTool(toolName)
                .orElse(null);

        if (tool == null) {
            log.warn("Attempted to dispatch unknown tool: {}", toolName);
            return ToolExecutionResult.failure("Unknown tool: '" + toolName + "'. Available tools: "
                    + toolRegistry.getAllToolDefinitions().stream().map(ToolDefinition::name).toList());
        }

        SpendSenseUserPrincipal principal;
        try {
            principal = currentUser.require();
        } catch (Exception e) {
            log.error("Authentication missing during tool dispatch: {}", e.getMessage());
            return ToolExecutionResult.failure("Authentication required for tool execution");
        }

        return executeTypedTool(tool, arguments, principal);
    }

    @SuppressWarnings("unchecked")
    private <TInput, TOutput> ToolExecutionResult<TOutput> executeTypedTool(
            FinancialTool<TInput, TOutput> tool,
            Map<String, Object> arguments,
            SpendSenseUserPrincipal principal
    ) {
        TInput typedInput;
        try {
            if (arguments == null || arguments.isEmpty()) {
                typedInput = objectMapper.convertValue(Map.of(), tool.getInputType());
            } else {
                typedInput = objectMapper.convertValue(arguments, tool.getInputType());
            }
        } catch (Exception e) {
            log.warn("Failed to parse arguments for tool {}: {}", tool.getName(), e.getMessage());
            return ToolExecutionResult.failure("Invalid arguments for tool '" + tool.getName() + "': " + e.getMessage());
        }

        if (typedInput != null && validator != null) {
            Set<ConstraintViolation<TInput>> violations = validator.validate(typedInput);
            if (!violations.isEmpty()) {
                String errorMsg = violations.stream()
                        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                        .collect(Collectors.joining("; "));
                log.warn("Validation error for tool {}: {}", tool.getName(), errorMsg);
                return ToolExecutionResult.failure("Validation failed for '" + tool.getName() + "': " + errorMsg);
            }
        }

        try {
            return tool.execute(typedInput, principal);
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", tool.getName(), e.getMessage(), e);
            return ToolExecutionResult.failure("Error executing '" + tool.getName() + "': " + e.getMessage());
        }
    }
}
