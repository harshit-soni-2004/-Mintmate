package com.spendsense.common.api;

import java.util.List;

public record ApiError(String code, String message, List<FieldErrorDetail> details) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }

    public record FieldErrorDetail(String field, String message) {
    }
}
