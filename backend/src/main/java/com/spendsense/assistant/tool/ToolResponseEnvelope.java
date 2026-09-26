package com.spendsense.assistant.tool;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * Standardized envelope for deterministic tool output.
 * Ensures the model receives unambiguous, verified backend data.
 *
 * @param <T> Payload data type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolResponseEnvelope<T>(
        boolean verified,
        String currency,
        PeriodDto period,
        String scope,
        T data,
        String summaryText,
        List<String> warnings
) {
    public static <T> ToolResponseEnvelope<T> of(T data, String summaryText) {
        return new ToolResponseEnvelope<>(true, "INR", null, "USER", data, summaryText, Collections.emptyList());
    }

    public static <T> ToolResponseEnvelope<T> of(T data, LocalDate start, LocalDate end, String summaryText) {
        return new ToolResponseEnvelope<>(true, "INR", new PeriodDto(start, end), "USER", data, summaryText, Collections.emptyList());
    }

    public static <T> ToolResponseEnvelope<T> of(T data, LocalDate start, LocalDate end, String scope, String summaryText, List<String> warnings) {
        return new ToolResponseEnvelope<>(true, "INR", new PeriodDto(start, end), scope, data, summaryText, warnings != null ? warnings : Collections.emptyList());
    }

    public record PeriodDto(LocalDate start, LocalDate end) {}
}
