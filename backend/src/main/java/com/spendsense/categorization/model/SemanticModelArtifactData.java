package com.spendsense.categorization.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for deserializing the Phase 6F semantic classifier artifact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SemanticModelArtifactData {

    @JsonProperty("metadata")
    private ModelMetadata metadata;

    @JsonProperty("intercept")
    private double[] intercept;

    @JsonProperty("coefficients")
    private double[][] coefficients;

    public ModelMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(ModelMetadata metadata) {
        this.metadata = metadata;
    }

    public double[] getIntercept() {
        return intercept;
    }

    public void setIntercept(double[] intercept) {
        this.intercept = intercept;
    }

    public double[][] getCoefficients() {
        return coefficients;
    }

    public void setCoefficients(double[][] coefficients) {
        this.coefficients = coefficients;
    }
}
