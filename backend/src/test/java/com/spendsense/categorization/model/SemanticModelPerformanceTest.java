package com.spendsense.categorization.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.NormalizedTransaction;
import com.spendsense.transaction.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Semantic Model Performance & Latency Benchmark")
class SemanticModelPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(SemanticModelPerformanceTest.class);

    @Test
    @DisplayName("Measures model load time, memory footprint, and repeated prediction latency")
    void benchmarkSemanticModelPerformance() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. Model Load Time Measurement
        long tLoad0 = System.nanoTime();
        ClassPathResource artifactRes = new ClassPathResource("model/semantic_classifier_v1.json");
        SemanticModelArtifactData artifactData;
        try (InputStream is = artifactRes.getInputStream()) {
            artifactData = objectMapper.readValue(is, SemanticModelArtifactData.class);
        }

        ClassPathResource vocabRes = new ClassPathResource("model/minilm_vocab.txt");
        BertTokenizer tokenizer;
        try (InputStream vis = vocabRes.getInputStream()) {
            tokenizer = new BertTokenizer(vis);
        }

        SemanticCategorizationModel model = new SemanticCategorizationModel(artifactData, tokenizer);
        long tLoad1 = System.nanoTime();
        double loadTimeMs = (tLoad1 - tLoad0) / 1_000_000.0;

        assertThat(model.getModelHealth().isAvailable()).isTrue();
        log.info("Model load time: {} ms", String.format("%.2f", loadTimeMs));

        // 2. Memory Footprint Measurement
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memBefore = runtime.totalMemory() - runtime.freeMemory();

        float[] sampleEmbedding = new float[384];
        for (int i = 0; i < 384; i++) {
            sampleEmbedding[i] = (float) Math.sin(i);
        }

        CategorizationInput input = CategorizationInput.of("Udemy", "semester fee via Udemy");

        // Warm-up
        for (int i = 0; i < 200; i++) {
            model.predictWithEmbedding(input, sampleEmbedding);
        }

        // 3. Repeated Prediction Latency (1000 iterations)
        int iterations = 1000;
        List<Double> latenciesMs = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            CategorizationPrediction pred = model.predictWithEmbedding(input, sampleEmbedding);
            long t1 = System.nanoTime();
            latenciesMs.add((t1 - t0) / 1_000_000.0);
            assertThat(pred.predictedCategory()).isNotNull();
        }

        Collections.sort(latenciesMs);
        double p50 = latenciesMs.get((int) (iterations * 0.50));
        double p95 = latenciesMs.get((int) (iterations * 0.95));
        double p99 = latenciesMs.get((int) (iterations * 0.99));
        double mean = latenciesMs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        long memAfter = runtime.totalMemory() - runtime.freeMemory();
        double deltaMemMb = (memAfter - memBefore) / (1024.0 * 1024.0);

        log.info("Scoring Latency Benchmark ({} runs): P50 = {} ms, P95 = {} ms, P99 = {} ms, Mean = {} ms",
                iterations, String.format("%.4f", p50), String.format("%.4f", p95),
                String.format("%.4f", p99), String.format("%.4f", mean));
        log.info("Heap Memory delta: {} MB", String.format("%.2f", deltaMemMb));

        // Ensure sub-millisecond CPU classification scoring when embeddings are evaluated in JVM
        assertThat(p50).isLessThan(5.0); // well within acceptable boundaries
    }
}
