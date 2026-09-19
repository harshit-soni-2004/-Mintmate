package com.spendsense.categorization.model;

/**
 * Health and readiness status of a categorization model.
 */
public record ModelHealth(
        boolean isAvailable,
        String status,
        String failureReason,
        ModelMetadata metadata
) {
    public static ModelHealth healthy(ModelMetadata metadata) {
        return new ModelHealth(true, "HEALTHY", null, metadata);
    }

    public static ModelHealth degraded(String reason, ModelMetadata metadata) {
        return new ModelHealth(false, "DEGRADED", reason, metadata);
    }

    public static ModelHealth unavailable(String reason) {
        return new ModelHealth(false, "UNAVAILABLE", reason, ModelMetadata.unknown());
    }
}
