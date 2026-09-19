package com.spendsense.categorization.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.NormalizedTransaction;
import com.spendsense.categorization.TransactionNormalizer;
import com.spendsense.transaction.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Baseline Categorization Model Inference Tests")
class BaselineCategorizationModelTest {

    private ObjectMapper objectMapper;
    private BaselineCategorizationModel model;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        model = new BaselineCategorizationModel(
                new ClassPathResource("model/baseline_model_v1.json"),
                objectMapper
        );
    }

    @Nested
    @DisplayName("Model Lifecycle & Health Checks")
    class LifecycleAndHealthTests {

        @Test
        @DisplayName("Should successfully load baseline model artifact and report healthy")
        void shouldLoadDefaultArtifactSuccessfully() {
            ModelHealth health = model.getModelHealth();
            assertThat(health.isAvailable()).isTrue();
            assertThat(health.status()).isEqualTo("HEALTHY");
            assertThat(health.failureReason()).isNull();

            ModelMetadata metadata = model.getModelMetadata();
            assertThat(metadata.modelVersion()).isEqualTo("v1.0.0-baseline-tfidf-lr");
            assertThat(metadata.datasetVersion()).isEqualTo("us_bank_categories_v2_processed_v1");
            assertThat(metadata.algorithm()).contains("Logistic Regression");
            assertThat(metadata.classes()).containsExactly(
                    "BILLS", "EDUCATION", "ENTERTAINMENT", "FOOD",
                    "HEALTH", "OTHER", "PERSONAL", "SHOPPING", "TRAVEL"
            );
        }

        @Test
        @DisplayName("Should fail gracefully when artifact file does not exist")
        void shouldHandleMissingArtifactGracefully() {
            BaselineCategorizationModel missingModel = new BaselineCategorizationModel(
                    new ClassPathResource("model/non_existent_model_file.json"),
                    objectMapper
            );

            ModelHealth health = missingModel.getModelHealth();
            assertThat(health.isAvailable()).isFalse();
            assertThat(health.status()).isEqualTo("UNAVAILABLE");
            assertThat(health.failureReason()).contains("not found");

            assertThatThrownBy(() -> missingModel.predict(CategorizationInput.of("starbucks coffee")))
                    .isInstanceOf(ModelInferenceException.class)
                    .hasMessageContaining("currently unavailable");
        }

        @Test
        @DisplayName("Should fail gracefully when artifact JSON is corrupted")
        void shouldHandleCorruptedArtifactGracefully() {
            byte[] badJson = "{ \"metadata\": { \"modelVersion\": \"corrupt\" ".getBytes(StandardCharsets.UTF_8);
            BaselineCategorizationModel corruptModel = new BaselineCategorizationModel(
                    new ByteArrayResource(badJson, "corrupt.json"),
                    objectMapper
            );

            ModelHealth health = corruptModel.getModelHealth();
            assertThat(health.isAvailable()).isFalse();
            assertThat(health.status()).isEqualTo("UNAVAILABLE");
            assertThat(health.failureReason()).contains("Failed to load/parse model artifact");

            assertThatThrownBy(() -> corruptModel.predict(CategorizationInput.of("starbucks coffee")))
                    .isInstanceOf(ModelInferenceException.class)
                    .hasMessageContaining("currently unavailable");
        }

        @Test
        @DisplayName("Should fail gracefully when artifact metadata or weights are missing")
        void shouldHandleIncompleteArtifactGracefully() {
            byte[] incompleteJson = "{ \"metadata\": { \"classes\": [] } }".getBytes(StandardCharsets.UTF_8);
            BaselineCategorizationModel incompleteModel = new BaselineCategorizationModel(
                    new ByteArrayResource(incompleteJson, "incomplete.json"),
                    objectMapper
            );

            ModelHealth health = incompleteModel.getModelHealth();
            assertThat(health.isAvailable()).isFalse();
            assertThat(health.status()).isEqualTo("UNAVAILABLE");
        }
    }

    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {

        @Test
        @DisplayName("Should reject null CategorizationInput")
        void shouldRejectNullInput() {
            assertThatThrownBy(() -> model.predict(null))
                    .isInstanceOf(ModelInferenceException.class)
                    .hasMessageContaining("CategorizationInput cannot be null");
        }

        @Test
        @DisplayName("Should reject empty or blank text")
        void shouldRejectBlankInput() {
            assertThatThrownBy(() -> model.predict(CategorizationInput.of("   ")))
                    .isInstanceOf(ModelInferenceException.class)
                    .hasMessageContaining("empty or blank");

            assertThatThrownBy(() -> model.predict(new CategorizationInput(null, null, null, null, null)))
                    .isInstanceOf(ModelInferenceException.class)
                    .hasMessageContaining("empty or blank");
        }

        @Test
        @DisplayName("Should fallback to normalizedMerchant when compositeText is absent")
        void shouldFallbackToNormalizedMerchant() {
            CategorizationInput input = new CategorizationInput("walmart supercenter", null, null, null, null);
            CategorizationPrediction prediction = model.predict(input);

            assertThat(prediction.predictedCategory()).isEqualTo(TransactionCategory.SHOPPING);
            assertThat(prediction.confidence()).isGreaterThan(0.90);
        }
    }

    @Nested
    @DisplayName("Scikit-Learn Numerical Equivalence Tests")
    class NumericalEquivalenceTests {

        public record TestVector(
                String inputText,
                String expectedCategory,
                double confidence,
                Map<String, Double> probabilities
        ) {}

        @Test
        @DisplayName("Should exactly reproduce scikit-learn predictions and probabilities across all test vectors")
        void shouldMatchScikitLearnPredictionsExactly() throws Exception {
            ClassPathResource vectorsResource = new ClassPathResource("model/baseline_test_vectors.json");
            assertThat(vectorsResource.exists()).isTrue();

            List<TestVector> testVectors;
            try (InputStream is = vectorsResource.getInputStream()) {
                testVectors = objectMapper.readValue(is, new TypeReference<>() {});
            }

            assertThat(testVectors).isNotEmpty();

            for (TestVector vector : testVectors) {
                CategorizationPrediction prediction = model.predict(CategorizationInput.of(vector.inputText()));

                // 1. Assert predicted category matches scikit-learn
                assertThat(prediction.predictedCategory().name())
                        .as("Category prediction for '%s'", vector.inputText())
                        .isEqualTo(vector.expectedCategory());

                // 2. Assert top confidence matches within 1e-4 tolerance
                assertThat(prediction.confidence())
                        .as("Top confidence for '%s'", vector.inputText())
                        .isCloseTo(vector.confidence(), org.assertj.core.data.Offset.offset(1e-4));

                // 3. Assert all class probabilities match within 1e-4 tolerance
                for (CategorizationCandidate candidate : prediction.alternatives()) {
                    String catName = candidate.category().name();
                    Double expectedProb = vector.probabilities().get(catName);
                    assertThat(expectedProb).isNotNull();

                    assertThat(candidate.confidence())
                            .as("Probability of class '%s' for '%s'", catName, vector.inputText())
                            .isCloseTo(expectedProb, org.assertj.core.data.Offset.offset(1e-4));
                }
            }
        }

        @Test
        @DisplayName("Should rank all 9 categories strictly descending and confidences sum to 1.0")
        void shouldRankCandidatesStrictlyDescending() {
            CategorizationPrediction prediction = model.predict(CategorizationInput.of("starbucks coffee seattle"));

            assertThat(prediction.alternatives()).hasSize(9);

            double sum = 0.0;
            double prevConfidence = Double.MAX_VALUE;
            for (CategorizationCandidate candidate : prediction.alternatives()) {
                assertThat(candidate.confidence()).isLessThanOrEqualTo(prevConfidence);
                prevConfidence = candidate.confidence();
                sum += candidate.confidence();
            }

            assertThat(sum).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
            assertThat(prediction.predictedCategory()).isEqualTo(prediction.alternatives().get(0).category());
            assertThat(prediction.confidence()).isEqualTo(prediction.alternatives().get(0).confidence());
        }

        @Test
        @DisplayName("Should maintain bitwise determinism over repeated predictions")
        void shouldBeDeterministicAcrossInvocations() {
            CategorizationInput input = CategorizationInput.of("netflix streaming sub");
            CategorizationPrediction first = model.predict(input);

            for (int i = 0; i < 20; i++) {
                CategorizationPrediction repeat = model.predict(input);
                assertThat(repeat.predictedCategory()).isEqualTo(first.predictedCategory());
                assertThat(repeat.confidence()).isEqualTo(first.confidence());
                assertThat(repeat.alternatives()).isEqualTo(first.alternatives());
            }
        }
    }

    @Nested
    @DisplayName("End-to-End Normalizer Integration")
    class NormalizerIntegrationTests {

        private TransactionNormalizer normalizer;

        @BeforeEach
        void initNormalizer() {
            normalizer = new TransactionNormalizer();
        }

        @Test
        @DisplayName("Should classify normalized real-world noisy transaction strings correctly")
        void shouldClassifyNormalizedTransactions() {
            NormalizedTransaction normUber = normalizer.normalize(
                    "UBER *TRIP 123456",
                    "SAN FRANCISCO CA VISA_DRAFT 9876"
            );
            CategorizationPrediction predUber = model.predict(CategorizationInput.from(normUber));
            assertThat(predUber.predictedCategory()).isEqualTo(TransactionCategory.TRAVEL);
            assertThat(predUber.confidence()).isGreaterThan(0.85);

            NormalizedTransaction normTarget = normalizer.normalize(
                    "TARGET #1234 STORE",
                    "PURCHASE 09/12 MINNEAPOLIS MN"
            );
            CategorizationPrediction predTarget = model.predict(CategorizationInput.from(normTarget));
            assertThat(predTarget.predictedCategory()).isEqualTo(TransactionCategory.SHOPPING);
            assertThat(predTarget.confidence()).isGreaterThan(0.90);
        }
    }

    @Nested
    @DisplayName("Cross-Market / Indian UPI Limitation Tests (Scientific Baseline Boundary)")
    class CrossMarketLimitationTests {

        @Test
        @DisplayName("Should verify known domain transfer limitations on Indian UPI transactions")
        void shouldDemonstrateDomainTransferLimitations() {
            // Case 1: Indian food delivery with UPI handle
            // Scikit-learn Baseline predicts FOOD with only ~0.2836 confidence (very low confidence due to unseen handles)
            CategorizationPrediction upiSwiggy = model.predict(
                    CategorizationInput.of("upi swiggy 123456 okhdfcbank")
            );
            assertThat(upiSwiggy.predictedCategory()).isEqualTo(TransactionCategory.FOOD);
            assertThat(upiSwiggy.confidence())
                    .as("Baseline confidence on unseen UPI handle swiggy is low (< 0.40)")
                    .isLessThan(0.40);

            // Case 2: Indian utility bill via Paytm
            // Scikit-learn Baseline misclassifies this as TRAVEL with ~0.3313 confidence because 'paytm' is unseen
            // and char n-grams trigger spurious features.
            CategorizationPrediction paytmBill = model.predict(
                    CategorizationInput.of("paytm billpay electricity")
            );
            assertThat(paytmBill.confidence())
                    .as("Paytm billpay confidence is low (< 0.45), indicating domain confusion")
                    .isLessThan(0.45);
            // Documenting that the baseline does NOT confidently classify this as BILLS
            assertThat(paytmBill.predictedCategory()).isNotEqualTo(TransactionCategory.BILLS);

            // Case 3: P2P transfer with Indian personal name handle
            CategorizationPrediction p2pTransfer = model.predict(
                    CategorizationInput.of("upi p sharma merchant okaxis")
            );
            assertThat(p2pTransfer.confidence())
                    .as("Baseline has no P2P semantic understanding and confidence is low (< 0.35)")
                    .isLessThan(0.35);
        }
    }
}
