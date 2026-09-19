package com.spendsense.categorization.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.List;

/**
 * Metadata describing a trained ML model artifact and its lineage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelMetadata(
        String modelVersion,
        String datasetVersion,
        String preprocessingVersion,
        String algorithm,
        String trainedAt,
        List<String> classes
) {
    public ModelMetadata {
        classes = classes != null ? Collections.unmodifiableList(classes) : Collections.emptyList();
    }

    public static ModelMetadata unknown() {
        return new ModelMetadata("unknown", "unknown", "unknown", "unknown", "unknown", Collections.emptyList());
    }
}
