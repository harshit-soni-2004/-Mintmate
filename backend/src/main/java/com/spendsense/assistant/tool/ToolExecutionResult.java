package com.spendsense.assistant.tool;

/**
 * Result of a tool execution containing either verified response data or structured error.
 *
 * @param <T> data type
 */
public record ToolExecutionResult<T>(
        boolean success,
        ToolResponseEnvelope<T> response,
        String errorMessage,
        boolean requiresConfirmation,
        Object pendingAction
) {
    public static <T> ToolExecutionResult<T> success(ToolResponseEnvelope<T> response) {
        return new ToolExecutionResult<>(true, response, null, false, null);
    }

    public static <T> ToolExecutionResult<T> failure(String errorMessage) {
        return new ToolExecutionResult<>(false, null, errorMessage, false, null);
    }

    public static <T> ToolExecutionResult<T> confirmationRequired(String message, Object pendingAction) {
        return new ToolExecutionResult<>(true, ToolResponseEnvelope.of(null, message), null, true, pendingAction);
    }

    public boolean isSuccess() {
        return success;
    }
}
