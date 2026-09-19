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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Production-ready hybrid transaction categorization model implementing the Phase 6F
 * validated architecture: dense 384-dimensional MiniLM embeddings concatenated with
 * sparse word+char TF-IDF n-grams behind Multinomial Logistic Regression.
 */
@Component
@Qualifier("hybridCategorizationModel")
public class HybridCategorizationModel implements CategorizationModel {

    private static final Logger log = LoggerFactory.getLogger(HybridCategorizationModel.class);
    private static final int MAX_SEQ_LENGTH = 64;
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w{2,}\\b", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CHAR_WORD_PATTERN = Pattern.compile("\\S+");

    private final boolean enabled;
    private final String artifactPath;
    private final String onnxPath;
    private final String vocabPath;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    private ModelMetadata metadata;
    private ModelHealth health;
    private int embeddingDim = 384;
    private Map<String, Integer> wordVocabulary;
    private double[] wordIdf;
    private Map<String, Integer> charVocabulary;
    private double[] charIdf;
    private double[] intercept;
    private double[][] coefficients;
    private TransactionCategory[] classCategories;
    private BertTokenizer tokenizer;

    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;

    @Autowired
    public HybridCategorizationModel(
            @Value("${spendsense.categorization.hybrid.enabled:false}") boolean enabled,
            @Value("${spendsense.categorization.hybrid.artifact-path:classpath:model/hybrid_model_v1.json}") String artifactPath,
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

    public HybridCategorizationModel(
            HybridModelArtifactData artifactData,
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
            this.health = ModelHealth.unavailable("Hybrid categorization is disabled by configuration");
            return;
        }

        log.info("Initializing hybrid categorization model from: {}", artifactPath);
        try {
            Resource artifactRes = resourceLoader.getResource(artifactPath);
            if (!artifactRes.exists()) {
                String error = "Hybrid model artifact not found at: " + artifactPath;
                this.health = ModelHealth.unavailable(error);
                return;
            }
            try (InputStream is = artifactRes.getInputStream()) {
                HybridModelArtifactData data = objectMapper.readValue(is, HybridModelArtifactData.class);
                initializeFromData(data);
            }

            Resource vocabRes = resourceLoader.getResource(vocabPath);
            if (vocabRes.exists()) {
                try (InputStream vis = vocabRes.getInputStream()) {
                    this.tokenizer = new BertTokenizer(vis);
                }
            }

            File onnxFile = new File(onnxPath);
            if (!onnxFile.exists()) {
                onnxFile = new File(System.getProperty("user.dir"), onnxPath);
            }

            if (onnxFile.exists()) {
                try {
                    this.ortEnvironment = OrtEnvironment.getEnvironment();
                    OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                    this.ortSession = ortEnvironment.createSession(onnxFile.getAbsolutePath(), opts);
                    this.health = ModelHealth.healthy(this.metadata);
                    log.info("Hybrid categorization model initialized successfully: version={}", metadata.modelVersion());
                } catch (Throwable t) {
                    String warn = "ONNX runtime failed for hybrid model: " + t.getMessage();
                    this.health = ModelHealth.unavailable(warn);
                }
            } else {
                this.health = ModelHealth.unavailable("ONNX file not found at: " + onnxPath);
            }

        } catch (Exception e) {
            this.health = ModelHealth.unavailable("Hybrid model initialization error: " + e.getMessage());
        }
    }

    private void initializeFromData(HybridModelArtifactData data) {
        if (data == null || data.getMetadata() == null) {
            this.health = ModelHealth.unavailable("Invalid hybrid artifact: Missing metadata");
            return;
        }

        List<String> rawClasses = data.getMetadata().classes();
        this.metadata = data.getMetadata();
        this.embeddingDim = data.getEmbeddingDim() > 0 ? data.getEmbeddingDim() : 384;
        this.wordVocabulary = Collections.unmodifiableMap(data.getWordVocabulary());
        this.wordIdf = data.getWordIdf();
        this.charVocabulary = Collections.unmodifiableMap(data.getCharVocabulary());
        this.charIdf = data.getCharIdf();
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
            throw new ModelInferenceException("Hybrid model unavailable: " + (health != null ? health.failureReason() : ""));
        }

        if (ortSession == null || ortEnvironment == null || tokenizer == null) {
            throw new ModelInferenceException("Hybrid runtime components not fully initialized");
        }

        String text = input.compositeText();
        if (text == null || text.isBlank()) {
            text = input.normalizedMerchant();
        }

        try {
            BertTokenizer.TokenizerResult tok = tokenizer.tokenize(text, MAX_SEQ_LENGTH);
            long[] shape = new long[]{1, MAX_SEQ_LENGTH};
            OnnxTensor tIds = OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(tok.inputIds()), shape);
            OnnxTensor tMask = OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(tok.attentionMask()), shape);
            OnnxTensor tType = OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(tok.tokenTypeIds()), shape);

            float[] embedding;
            try (OrtSession.Result res = ortSession.run(Map.of("input_ids", tIds, "attention_mask", tMask, "token_type_ids", tType))) {
                float[][] out = (float[][]) res.get(0).getValue();
                embedding = out[0];
            } finally {
                tIds.close();
                tMask.close();
                tType.close();
            }

            return predictWithEmbedding(input, embedding);
        } catch (Exception e) {
            throw new ModelInferenceException("Hybrid inference failed: " + e.getMessage(), e);
        }
    }

    public CategorizationPrediction predictWithEmbedding(CategorizationInput input, float[] embedding) {
        if (embedding == null || embedding.length != embeddingDim) {
            throw new ModelInferenceException("Expected " + embeddingDim + "-dim embedding");
        }

        String text = input.compositeText();
        if (text == null || text.isBlank()) {
            text = input.normalizedMerchant();
        }
        String normalized = text.toLowerCase(Locale.ROOT);

        // 1. Word TF-IDF
        Matcher wordMatcher = WORD_PATTERN.matcher(normalized);
        List<String> tokens = new ArrayList<>();
        while (wordMatcher.find()) {
            tokens.add(wordMatcher.group());
        }
        Map<String, Integer> wordCounts = new HashMap<>();
        for (String t : tokens) {
            wordCounts.put(t, wordCounts.getOrDefault(t, 0) + 1);
        }
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + " " + tokens.get(i + 1);
            wordCounts.put(bigram, wordCounts.getOrDefault(bigram, 0) + 1);
        }
        Map<Integer, Double> wordFeats = new HashMap<>();
        for (Map.Entry<String, Integer> e : wordCounts.entrySet()) {
            Integer idx = wordVocabulary.get(e.getKey());
            if (idx != null) {
                double tf = 1.0 + Math.log(e.getValue());
                wordFeats.put(idx, tf * wordIdf[idx]);
            }
        }
        double wordNormSq = 0.0;
        for (double v : wordFeats.values()) wordNormSq += v * v;
        if (wordNormSq > 0.0) {
            double norm = Math.sqrt(wordNormSq);
            for (Map.Entry<Integer, Double> e : wordFeats.entrySet()) e.setValue(e.getValue() / norm);
        }

        // 2. Char TF-IDF
        Matcher charMatcher = CHAR_WORD_PATTERN.matcher(normalized);
        Map<String, Integer> charNgramCounts = new HashMap<>();
        while (charMatcher.find()) {
            String paddedWord = " " + charMatcher.group() + " ";
            int len = paddedWord.length();
            for (int n = 3; n <= 5; n++) {
                for (int i = 0; i <= len - n; i++) {
                    String ngram = paddedWord.substring(i, i + n);
                    charNgramCounts.put(ngram, charNgramCounts.getOrDefault(ngram, 0) + 1);
                }
            }
        }
        Map<Integer, Double> charFeats = new HashMap<>();
        for (Map.Entry<String, Integer> e : charNgramCounts.entrySet()) {
            Integer idx = charVocabulary.get(e.getKey());
            if (idx != null) {
                double tf = 1.0 + Math.log(e.getValue());
                charFeats.put(idx, tf * charIdf[idx]);
            }
        }
        double charNormSq = 0.0;
        for (double v : charFeats.values()) charNormSq += v * v;
        if (charNormSq > 0.0) {
            double norm = Math.sqrt(charNormSq);
            for (Map.Entry<Integer, Double> e : charFeats.entrySet()) e.setValue(e.getValue() / norm);
        }

        // 3. Score calculation
        int numClasses = classCategories.length;
        double[] z = new double[numClasses];
        System.arraycopy(intercept, 0, z, 0, numClasses);

        int wordOffset = embeddingDim;
        int charOffset = embeddingDim + wordVocabulary.size();

        for (int c = 0; c < numClasses; c++) {
            double[] w = coefficients[c];
            // Dense embedding contribution
            double embDot = 0.0;
            for (int j = 0; j < embeddingDim; j++) {
                embDot += w[j] * embedding[j];
            }
            z[c] += embDot;

            // Word TF-IDF contribution
            for (Map.Entry<Integer, Double> e : wordFeats.entrySet()) {
                z[c] += w[wordOffset + e.getKey()] * e.getValue();
            }

            // Char TF-IDF contribution
            for (Map.Entry<Integer, Double> e : charFeats.entrySet()) {
                z[c] += w[charOffset + e.getKey()] * e.getValue();
            }
        }

        // 4. Softmax
        double maxZ = z[0];
        for (int c = 1; c < numClasses; c++) if (z[c] > maxZ) maxZ = z[c];

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
                metadata != null ? metadata.modelVersion() : "v1.0.0-hybrid-minilm-tfidf"
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
            try { ortSession.close(); } catch (Exception ignored) {}
        }
        if (ortEnvironment != null) {
            try { ortEnvironment.close(); } catch (Exception ignored) {}
        }
    }
}
