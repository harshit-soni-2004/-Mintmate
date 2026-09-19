package com.spendsense.categorization.model;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.transaction.TransactionCategory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Production-ready semantic transaction categorization model leveraging the
 * Phase 6F validated all-MiniLM-L6-v2 transformer embedding architecture.
 * <p>
 * Evaluates dense 384-dimensional representations locally in-process via ONNX Runtime,
 * with zero external API calls, zero data egress, and graceful failure isolation.
 */
@Component
@Qualifier("semanticCategorizationModel")
public class SemanticCategorizationModel implements CategorizationModel {

    private static final Logger log = LoggerFactory.getLogger(SemanticCategorizationModel.class);
    private static final int MAX_SEQ_LENGTH = 64;

    private final boolean enabled;
    private final String artifactPath;
    private final String onnxPath;
    private final String vocabPath;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private ModelMetadata metadata;
    private ModelHealth health;
    private double[] intercept;
    private double[][] coefficients;
    private TransactionCategory[] classCategories;
    private BertTokenizer tokenizer;

    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;

    @Autowired
    public SemanticCategorizationModel(
            @Value("${spendsense.categorization.semantic.enabled:false}") boolean enabled,
            @Value("${spendsense.categorization.semantic.artifact-path:classpath:model/semantic_classifier_v1.json}") String artifactPath,
            @Value("${spendsense.categorization.semantic.onnx-path:ml/models/semantic_v1/minilm_l6_v2.onnx}") String onnxPath,
            @Value("${spendsense.categorization.semantic.vocab-path:classpath:model/minilm_vocab.txt}") String vocabPath,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.artifactPath = artifactPath;
        this.onnxPath = onnxPath;
        this.vocabPath = vocabPath;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public SemanticCategorizationModel(
            SemanticModelArtifactData artifactData,
            BertTokenizer tokenizer
    ) {
        this.enabled = true;
        this.artifactPath = "in-memory";
        this.onnxPath = null;
        this.vocabPath = null;
        this.resourceLoader = null;
        this.objectMapper = new ObjectMapper();
        this.tokenizer = tokenizer;
        initializeFromData(artifactData);
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            this.health = ModelHealth.unavailable("Semantic categorization is disabled by configuration");
            log.info("Semantic categorization model is disabled (spendsense.categorization.semantic.enabled=false)");
            return;
        }

        log.info("Initializing semantic categorization model from artifact: {}", artifactPath);
        try {
            // 1. Load Classifier weights JSON
            Resource classifierRes = resourceLoader.getResource(artifactPath);
            if (!classifierRes.exists()) {
                String error = "Semantic model artifact not found at: " + artifactPath;
                log.warn(error);
                this.health = ModelHealth.unavailable(error);
                return;
            }
            try (InputStream is = classifierRes.getInputStream()) {
                SemanticModelArtifactData data = objectMapper.readValue(is, SemanticModelArtifactData.class);
                initializeFromData(data);
            }

            // 2. Load WordPiece Vocabulary
            Resource vocabRes = resourceLoader.getResource(vocabPath);
            if (vocabRes.exists()) {
                try (InputStream vis = vocabRes.getInputStream()) {
                    this.tokenizer = new BertTokenizer(vis);
                    log.info("Loaded WordPiece vocabulary with {} tokens", tokenizer.getVocabSize());
                }
            } else {
                log.warn("Tokenizer vocabulary not found at: {}", vocabPath);
            }

            // 3. Initialize ONNX Runtime Session if available
            File onnxFile = new File(onnxPath);
            if (!onnxFile.exists()) {
                // Check relative to user directory or workspace
                onnxFile = new File(System.getProperty("user.dir"), onnxPath);
            }

            if (onnxFile.exists()) {
                try {
                    this.ortEnvironment = OrtEnvironment.getEnvironment();
                    OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                    opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
                    this.ortSession = ortEnvironment.createSession(onnxFile.getAbsolutePath(), opts);
                    this.health = ModelHealth.healthy(this.metadata);
                    log.info("Semantic model loaded successfully with active ONNX Runtime: version={}, classes={}",
                            metadata.modelVersion(), metadata.classes().size());
                } catch (Throwable t) {
                    String warn = "ONNX Runtime initialization failed: " + t.getMessage();
                    log.warn(warn);
                    this.health = ModelHealth.unavailable(warn);
                }
            } else {
                String msg = "Semantic classifier parameters loaded, but ONNX binary is not found at: " + onnxPath;
                log.warn(msg);
                this.health = ModelHealth.unavailable(msg);
            }

        } catch (Exception e) {
            String error = "Failed to initialize semantic categorization model: " + e.getMessage();
            log.error(error, e);
            this.health = ModelHealth.unavailable(error);
        }
    }

    private void initializeFromData(SemanticModelArtifactData data) {
        if (data == null || data.getMetadata() == null) {
            this.health = ModelHealth.unavailable("Invalid semantic artifact: Missing metadata");
            return;
        }

        List<String> rawClasses = data.getMetadata().classes();
        if (rawClasses == null || rawClasses.isEmpty()) {
            this.health = ModelHealth.unavailable("Invalid semantic artifact: Missing classes");
            return;
        }

        if (data.getIntercept() == null || data.getCoefficients() == null) {
            this.health = ModelHealth.unavailable("Invalid semantic artifact: Missing weights/intercepts");
            return;
        }

        this.metadata = data.getMetadata();
        this.intercept = data.getIntercept();
        this.coefficients = data.getCoefficients();

        int numClasses = rawClasses.size();
        this.classCategories = new TransactionCategory[numClasses];
        for (int i = 0; i < numClasses; i++) {
            this.classCategories[i] = TransactionCategory.valueOf(rawClasses.get(i).trim());
        }

        if (this.health == null) {
            this.health = ModelHealth.healthy(this.metadata);
        }
    }

    @Override
    public CategorizationPrediction predict(CategorizationInput input) {
        if (health == null || !health.isAvailable()) {
            throw new ModelInferenceException("Semantic categorization model is currently unavailable: " +
                    (health != null ? health.failureReason() : "not initialized"));
        }

        if (input == null) {
            throw new ModelInferenceException("CategorizationInput cannot be null");
        }

        if (ortSession == null || ortEnvironment == null || tokenizer == null) {
            throw new ModelInferenceException("Semantic runtime components (ONNX/Tokenizer) are not initialized");
        }

        String textToClassify = input.compositeText();
        if (textToClassify == null || textToClassify.isBlank()) {
            textToClassify = input.normalizedMerchant();
        }

        if (textToClassify == null || textToClassify.isBlank()) {
            throw new ModelInferenceException("Cannot categorize transaction: text is empty or blank");
        }

        try {
            BertTokenizer.TokenizerResult tokenized = tokenizer.tokenize(textToClassify, MAX_SEQ_LENGTH);
            long[] inputIds = tokenized.inputIds();
            long[] attentionMask = tokenized.attentionMask();
            long[] tokenTypeIds = tokenized.tokenTypeIds();

            long[] shape = new long[]{1, MAX_SEQ_LENGTH};
            OnnxTensor tInputIds = OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(inputIds), shape);
            OnnxTensor tMask = OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(attentionMask), shape);
            OnnxTensor tTypes = OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(tokenTypeIds), shape);

            Map<String, OnnxTensor> inputs = Map.of(
                    "input_ids", tInputIds,
                    "attention_mask", tMask,
                    "token_type_ids", tTypes
            );

            float[] embedding;
            try (OrtSession.Result result = ortSession.run(inputs)) {
                float[][] batchEmbeddings = (float[][]) result.get(0).getValue();
                embedding = batchEmbeddings[0];
            } finally {
                tInputIds.close();
                tMask.close();
                tTypes.close();
            }

            return predictWithEmbedding(input, embedding);

        } catch (Exception e) {
            log.error("Semantic inference failed for input: {}", e.getMessage());
            throw new ModelInferenceException("Semantic model inference failed: " + e.getMessage(), e);
        }
    }

    /**
     * Scores a transaction using a precomputed or externally supplied 384-dimensional dense embedding.
     * Useful for deterministic testing, benchmarks, and decoupled embeddings.
     */
    public CategorizationPrediction predictWithEmbedding(CategorizationInput input, float[] embedding) {
        if (embedding == null || embedding.length != 384) {
            throw new ModelInferenceException("Expected 384-dimensional embedding, but received: " +
                    (embedding != null ? embedding.length : "null"));
        }

        int numClasses = classCategories.length;
        double[] z = new double[numClasses];
        System.arraycopy(intercept, 0, z, 0, numClasses);

        for (int c = 0; c < numClasses; c++) {
            double[] weights = coefficients[c];
            double dot = 0.0;
            for (int j = 0; j < 384; j++) {
                dot += weights[j] * embedding[j];
            }
            z[c] += dot;
        }

        // Softmax with numerical stability
        double maxZ = z[0];
        for (int c = 1; c < numClasses; c++) {
            if (z[c] > maxZ) {
                maxZ = z[c];
            }
        }

        double sumExp = 0.0;
        double[] expZ = new double[numClasses];
        for (int c = 0; c < numClasses; c++) {
            expZ[c] = Math.exp(z[c] - maxZ);
            sumExp += expZ[c];
        }

        List<CategorizationCandidate> candidates = new ArrayList<>(numClasses);
        for (int c = 0; c < numClasses; c++) {
            candidates.add(new CategorizationCandidate(classCategories[c], expZ[c] / sumExp));
        }
        Collections.sort(candidates);

        CategorizationCandidate top = candidates.get(0);
        return new CategorizationPrediction(
                top.category(),
                top.confidence(),
                candidates,
                metadata != null ? metadata.modelVersion() : "v1.0.0-semantic-minilm-l6"
        );
    }

    @Override
    public ModelMetadata getModelMetadata() {
        return metadata != null ? metadata : ModelMetadata.unknown();
    }

    @Override
    public ModelHealth getModelHealth() {
        return health != null ? health : ModelHealth.unavailable("Model not initialized");
    }

    @PreDestroy
    public void close() {
        if (ortSession != null) {
            try {
                ortSession.close();
            } catch (Exception e) {
                log.debug("Error closing OrtSession: {}", e.getMessage());
            }
        }
        if (ortEnvironment != null) {
            try {
                ortEnvironment.close();
            } catch (Exception e) {
                log.debug("Error closing OrtEnvironment: {}", e.getMessage());
            }
        }
    }
}
