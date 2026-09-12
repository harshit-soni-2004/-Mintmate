package com.spendsense.common.api;

public record PageMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public PageMeta(int page, int size, long totalElements, int totalPages) {
        this(page, size, totalElements, totalPages, page == 0, totalPages == 0 || page + 1 >= totalPages);
    }
}
