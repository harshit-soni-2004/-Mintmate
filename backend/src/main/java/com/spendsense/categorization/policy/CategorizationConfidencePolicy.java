package com.spendsense.categorization.policy;

import com.spendsense.categorization.ConfidenceLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized operational confidence policy component.
 * Decouples categorization threshold evaluation from ML and rule scoring components.
 */
@Component
public class CategorizationConfidencePolicy {

    private final double highThreshold;
    private final double mediumThreshold;

    public CategorizationConfidencePolicy(
            @Value("${spendsense.categorization.policy.high-threshold:0.85}") double highThreshold,
            @Value("${spendsense.categorization.policy.medium-threshold:0.60}") double mediumThreshold
    ) {
        if (highThreshold <= mediumThreshold) {
            throw new IllegalArgumentException("High threshold must be greater than medium threshold");
        }
        this.highThreshold = highThreshold;
        this.mediumThreshold = mediumThreshold;
    }

    /**
     * Determines the operational ConfidenceLevel based on a calibrated raw confidence score.
     */
    public ConfidenceLevel evaluateLevel(double confidence) {
        if (confidence >= highThreshold) {
            return ConfidenceLevel.HIGH;
        } else if (confidence >= mediumThreshold) {
            return ConfidenceLevel.MEDIUM;
        } else if (confidence > 0.0) {
            return ConfidenceLevel.LOW;
        } else {
            return ConfidenceLevel.NONE;
        }
    }

    /**
     * Returns true if the confidence level qualifies for automatic category assignment.
     */
    public boolean isAutoAssignEligible(ConfidenceLevel level) {
        return level == ConfidenceLevel.HIGH;
    }

    /**
     * Returns true if user verification/confirmation should be requested.
     */
    public boolean requiresConfirmation(ConfidenceLevel level) {
        return level != ConfidenceLevel.HIGH;
    }

    /**
     * Returns true if the prediction confidence is low or none, precluding persistence as a named category.
     */
    public boolean isLowConfidence(ConfidenceLevel level) {
        return level == null || level == ConfidenceLevel.LOW || level == ConfidenceLevel.NONE;
    }

    public double getHighThreshold() {
        return highThreshold;
    }

    public double getMediumThreshold() {
        return mediumThreshold;
    }
}
