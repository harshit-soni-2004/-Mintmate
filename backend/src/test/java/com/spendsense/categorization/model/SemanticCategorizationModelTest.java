package com.spendsense.categorization.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.NormalizedTransaction;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Semantic Categorization Model Tests")
class SemanticCategorizationModelTest {

    private ObjectMapper objectMapper;
    private SemanticModelArtifactData validArtifactData;
    private BertTokenizer tokenizer;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        ClassPathResource artifactRes = new ClassPathResource("model/semantic_classifier_v1.json");
        try (InputStream is = artifactRes.getInputStream()) {
            validArtifactData = objectMapper.readValue(is, SemanticModelArtifactData.class);
        }

        ClassPathResource vocabRes = new ClassPathResource("model/minilm_vocab.txt");
        try (InputStream vis = vocabRes.getInputStream()) {
            tokenizer = new BertTokenizer(vis);
        }
    }

    @Nested
    @DisplayName("Lifecycle, Metadata, and Health Tests")
    class LifecycleAndHealthTests {

        @Test
        @DisplayName("Loads successfully from valid artifact in memory")
        void testSuccessfulLoadFromData() {
            SemanticCategorizationModel model = new SemanticCategorizationModel(validArtifactData, tokenizer);
            assertThat(model.getModelHealth().isAvailable()).isTrue();
            assertThat(model.getModelHealth().status()).isEqualTo("HEALTHY");

            ModelMetadata metadata = model.getModelMetadata();
            assertThat(metadata.modelVersion()).isEqualTo("v1.0.0-semantic-minilm-l6");
            assertThat(metadata.classes()).containsExactly(
                    "BILLS", "EDUCATION", "ENTERTAINMENT", "FOOD",
                    "HEALTH", "OTHER", "PERSONAL", "SHOPPING", "TRAVEL"
            );
        }

        @Test
        @DisplayName("Handles disabled configuration safely without crashing")
        void testDisabledConfiguration() {
            SemanticCategorizationModel model = new SemanticCategorizationModel(
                    false,
                    "classpath:model/semantic_classifier_v1.json",
                    "dummy/path.onnx",
                    "classpath:model/minilm_vocab.txt",
                    new DefaultResourceLoader(),
                    objectMapper
            );
            model.initialize();

            assertThat(model.getModelHealth().isAvailable()).isFalse();
            assertThat(model.getModelHealth().failureReason()).contains("disabled by configuration");
        }

        @Test
        @DisplayName("Handles missing model artifact gracefully")
        void testMissingArtifact() {
            SemanticCategorizationModel model = new SemanticCategorizationModel(
                    true,
                    "classpath:model/non_existent_model.json",
                    "dummy/path.onnx",
                    "classpath:model/minilm_vocab.txt",
                    new DefaultResourceLoader(),
                    objectMapper
            );
            model.initialize();

            assertThat(model.getModelHealth().isAvailable()).isFalse();
            assertThat(model.getModelHealth().failureReason()).contains("not found");
        }

        @Test
        @DisplayName("Handles corrupt artifact gracefully")
        void testCorruptArtifact() {
            SemanticModelArtifactData corruptData = new SemanticModelArtifactData();
            corruptData.setMetadata(null); // Missing metadata

            SemanticCategorizationModel model = new SemanticCategorizationModel(corruptData, tokenizer);
            assertThat(model.getModelHealth().isAvailable()).isFalse();
            assertThat(model.getModelHealth().failureReason()).contains("Missing metadata");
        }

        @Test
        @DisplayName("Handles missing class definitions gracefully")
        void testMissingClasses() {
            SemanticModelArtifactData corruptData = new SemanticModelArtifactData();
            ModelMetadata meta = new ModelMetadata("v1", "ds", "pre", "algo", "time", List.of());
            corruptData.setMetadata(meta);

            SemanticCategorizationModel model = new SemanticCategorizationModel(corruptData, tokenizer);
            assertThat(model.getModelHealth().isAvailable()).isFalse();
            assertThat(model.getModelHealth().failureReason()).contains("Missing classes");
        }
    }

    @Nested
    @DisplayName("Prediction and Ranking Tests")
    class PredictionTests {

        @Test
        @DisplayName("Throws ModelInferenceException if model is unavailable")
        void testPredictWhenUnavailable() {
            SemanticModelArtifactData corruptData = new SemanticModelArtifactData();
            SemanticCategorizationModel model = new SemanticCategorizationModel(corruptData, tokenizer);

            CategorizationInput input = createInput("starbucks", "starbucks coffee");
            assertThatThrownBy(() -> model.predict(input))
                    .isInstanceOf(ModelInferenceException.class)
                    .hasMessageContaining("unavailable");
        }

        @Test
        @DisplayName("Evaluates predictions deterministically using precomputed Phase 6F embeddings")
        void testPredictWithEmbeddingVectors() throws Exception {
            SemanticCategorizationModel model = new SemanticCategorizationModel(validArtifactData, tokenizer);

            ClassPathResource vectorsRes = new ClassPathResource("model/semantic_test_vectors.json");
            List<Map<String, Object>> testVectors;
            try (InputStream is = vectorsRes.getInputStream()) {
                testVectors = objectMapper.readValue(is, List.class);
            }

            assertThat(testVectors).isNotEmpty();

            for (Map<String, Object> testCase : testVectors) {
                String text = (String) testCase.get("inputText");
                String expectedCategoryStr = (String) testCase.get("expectedCategory");
                TransactionCategory expectedCategory = TransactionCategory.valueOf(expectedCategoryStr);
                double expectedConfidence = ((Number) testCase.get("confidence")).doubleValue();

                List<Number> embList = (List<Number>) testCase.get("embedding");
                float[] emb = new float[embList.size()];
                for (int i = 0; i < embList.size(); i++) {
                    emb[i] = embList.get(i).floatValue();
                }

                CategorizationInput input = createInput(text, text);
                CategorizationPrediction prediction = model.predictWithEmbedding(input, emb);

                assertThat(prediction.predictedCategory())
                        .as("Category prediction for '%s'", text)
                        .isEqualTo(expectedCategory);

                assertThat(prediction.confidence())
                        .as("Confidence for '%s'", text)
                        .isCloseTo(expectedConfidence, within(1e-4));

                // Alternatives check
                assertThat(prediction.alternatives()).hasSize(9);
                assertThat(prediction.alternatives().get(0).category()).isEqualTo(expectedCategory);

                // Probabilities sum to 1.0
                double sumProb = prediction.alternatives().stream()
                        .mapToDouble(CategorizationCandidate::confidence)
                        .sum();
                assertThat(sumProb).isCloseTo(1.0, within(1e-4));

                // Ordered descending
                for (int i = 0; i < prediction.alternatives().size() - 1; i++) {
                    assertThat(prediction.alternatives().get(i).confidence())
                            .isGreaterThanOrEqualTo(prediction.alternatives().get(i + 1).confidence());
                }
            }
        }

        @Test
        @DisplayName("Rejects wrong-sized embedding array")
        void testInvalidEmbeddingDimension() {
            SemanticCategorizationModel model = new SemanticCategorizationModel(validArtifactData, tokenizer);
            CategorizationInput input = createInput("udemy", "udemy");

            assertThatThrownBy(() -> model.predictWithEmbedding(input, new float[128]))
                    .isInstanceOf(ModelInferenceException.class)
                    .hasMessageContaining("Expected 384-dimensional embedding");
        }

        @Test
        @DisplayName("Thread safety: concurrent predictions on multiple threads are deterministic")
        void testThreadSafety() throws Exception {
            SemanticCategorizationModel model = new SemanticCategorizationModel(validArtifactData, tokenizer);
            float[] sampleEmb = new float[384];
            sampleEmb[0] = 0.5f;
            sampleEmb[1] = -0.3f;
            sampleEmb[383] = 0.8f;

            CategorizationInput input = createInput("test merchant", "test desc");
            CategorizationPrediction reference = model.predictWithEmbedding(input, sampleEmb);

            int threads = 8;
            int iterationsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(threads);
            AtomicInteger mismatchCount = new AtomicInteger(0);

            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            CategorizationPrediction pred = model.predictWithEmbedding(input, sampleEmb);
                            if (pred.predictedCategory() != reference.predictedCategory() ||
                                    Math.abs(pred.confidence() - reference.confidence()) > 1e-6) {
                                mismatchCount.incrementAndGet();
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();

            assertThat(mismatchCount.get()).isEqualTo(0);
        }
    }

    private CategorizationInput createInput(String merchant, String text) {
        return CategorizationInput.of(merchant, text);
    }
}
