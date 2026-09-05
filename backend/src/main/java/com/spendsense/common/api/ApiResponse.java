package com.spendsense.common.api;

public record ApiResponse<T>(T data, ApiError error, PageMeta meta) {

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> of(T data, PageMeta meta) {
        return new ApiResponse<>(data, null, meta);
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(null, error, null);
    }
}
