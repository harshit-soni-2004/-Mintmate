package com.spendsense.categorization.policy;

import com.spendsense.categorization.ConfidenceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Categorization Confidence Policy Tests")
class CategorizationConfidencePolicyTest {

    private final CategorizationConfidencePolicy policy = new CategorizationConfidencePolicy(0.85, 0.60);

    @ParameterizedTest
    @CsvSource({
            "0.99, HIGH",
            "0.85, HIGH",
            "0.849, MEDIUM",
            "0.60, MEDIUM",
            "0.599, LOW",
            "0.10, LOW",
            "0.0, NONE"
    })
    @DisplayName("Should evaluate confidence levels correctly based on configured thresholds")
    void shouldEvaluateConfidenceLevels(double confidence, ConfidenceLevel expectedLevel) {
        assertThat(policy.evaluateLevel(confidence)).isEqualTo(expectedLevel);
    }

    @Test
    @DisplayName("Should validate threshold invariants on construction")
    void shouldRejectInvalidThresholds() {
        assertThatThrownBy(() -> new CategorizationConfidencePolicy(0.50, 0.80))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("High threshold must be greater than medium threshold");
    }

    @Test
    @DisplayName("Should correctly evaluate auto-assign eligibility and confirmation requirement")
    void shouldEvaluateDecisionFlags() {
        assertThat(policy.isAutoAssignEligible(ConfidenceLevel.HIGH)).isTrue();
        assertThat(policy.isAutoAssignEligible(ConfidenceLevel.MEDIUM)).isFalse();
        assertThat(policy.isAutoAssignEligible(ConfidenceLevel.LOW)).isFalse();

        assertThat(policy.requiresConfirmation(ConfidenceLevel.HIGH)).isFalse();
        assertThat(policy.requiresConfirmation(ConfidenceLevel.MEDIUM)).isTrue();
        assertThat(policy.requiresConfirmation(ConfidenceLevel.LOW)).isTrue();
        assertThat(policy.requiresConfirmation(ConfidenceLevel.NONE)).isTrue();
    }
}
