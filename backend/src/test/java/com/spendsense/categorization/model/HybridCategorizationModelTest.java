package com.spendsense.categorization.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.NormalizedTransaction;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Hybrid Categorization Model Tests")
class HybridCategorizationModelTest {

    private ObjectMapper objectMapper;
    private HybridModelArtifactData hybridData;
    private BertTokenizer tokenizer;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        ClassPathResource artifactRes = new ClassPathResource("model/hybrid_model_v1.json");
        try (InputStream is = artifactRes.getInputStream()) {
            hybridData = objectMapper.readValue(is, HybridModelArtifactData.class);
        }

        ClassPathResource vocabRes = new ClassPathResource("model/minilm_vocab.txt");
        try (InputStream vis = vocabRes.getInputStream()) {
            tokenizer = new BertTokenizer(vis);
        }
    }

    @Test
    @DisplayName("Loads successfully from valid hybrid artifact")
    void testSuccessfulLoad() {
        HybridCategorizationModel model = new HybridCategorizationModel(hybridData, tokenizer);
        assertThat(model.getModelHealth().isAvailable()).isTrue();
        assertThat(model.getModelMetadata().modelVersion()).isEqualTo("v1.0.0-hybrid-minilm-tfidf");
        assertThat(model.getModelMetadata().classes()).hasSize(9);
    }

    @Test
    @DisplayName("Predicts with dense embedding and sparse n-grams correctly")
    void testPredictWithEmbedding() throws Exception {
        HybridCategorizationModel model = new HybridCategorizationModel(hybridData, tokenizer);

        ClassPathResource tvRes = new ClassPathResource("model/hybrid_test_vectors.json");
        List<Map<String, Object>> testVectors;
        try (InputStream is = tvRes.getInputStream()) {
            testVectors = objectMapper.readValue(is, List.class);
        }

        for (Map<String, Object> testCase : testVectors) {
            String text = (String) testCase.get("inputText");
            List<Number> embList = (List<Number>) testCase.get("embedding");
            float[] emb = new float[embList.size()];
            for (int i = 0; i < embList.size(); i++) {
                emb[i] = embList.get(i).floatValue();
            }

            CategorizationInput input = CategorizationInput.of(text, text);

            CategorizationPrediction pred = model.predictWithEmbedding(input, emb);
            assertThat(pred.predictedCategory()).isNotNull();
            assertThat(pred.confidence()).isBetween(0.0, 1.0);
            assertThat(pred.alternatives()).hasSize(9);

            double sum = pred.alternatives().stream().mapToDouble(CategorizationCandidate::confidence).sum();
            assertThat(sum).isCloseTo(1.0, within(1e-4));
        }
    }
}
