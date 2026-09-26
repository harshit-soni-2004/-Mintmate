package com.spendsense.assistant.tool;

import com.spendsense.security.SpendSenseUserPrincipal;

import java.util.Map;

/**
 * Standard contract for all financial assistant tools.
 *
 * @param <TInput>  Strongly typed input parameters record/class
 * @param <TOutput> Strongly typed result data record/class
 */
public interface FinancialTool<TInput, TOutput> {

    /**
     * Unique stable name for the tool, e.g. "get_spending_summary".
     */
    String getName();

    /**
     * Natural language description of what the tool does and when to call it.
     */
    String getDescription();

    /**
     * Input class for Jackson deserialization and Bean Validation.
     */
    Class<TInput> getInputType();

    /**
     * JSON Schema specification of tool arguments for LLM function calling.
     */
    Map<String, Object> getParameterSchema();

    /**
     * Indicates whether this tool performs any mutation or state change.
     */
    boolean isMutating();

    /**
     * Deterministically executes the tool using the verified authenticated user principal.
     * The LLM cannot provide or override user identity.
     *
     * @param input parsed and validated input
     * @param user authenticated user principal
     * @return tool execution result containing verified response envelope
     */
    ToolExecutionResult<TOutput> execute(TInput input, SpendSenseUserPrincipal user);

    /**
     * Executes the confirmed state change after explicit user confirmation.
     * Default implementation throws UnsupportedOperationException for read-only tools.
     */
    default Object executeConfirmedMutation(String parametersJson, SpendSenseUserPrincipal user) {
        throw new UnsupportedOperationException("Mutation not supported for read-only tool: " + getName());
    }
}
