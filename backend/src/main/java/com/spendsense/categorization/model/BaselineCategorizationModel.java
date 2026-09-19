package com.spendsense.categorization.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.transaction.TransactionCategory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Baseline categorization model implementation wrapping the Phase 6C trained
 * TF-IDF Word+Char N-Gram + Multiclass Logistic Regression pipeline.
 * <p>
 * Provides 100% deterministic, in-process JVM inference matching Scikit-Learn
 * floating-point precision, with zero native JNI or external runtime dependencies.
 */
@Component
public class BaselineCategorizationModel implements CategorizationModel {

    private static final Logger log = LoggerFactory.getLogger(BaselineCategorizationModel.class);

    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w{2,}\\b", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CHAR_WORD_PATTERN = Pattern.compile("\\S+");

    private final String artifactPath;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final Resource explicitResource;

    private ModelMetadata metadata;
    private ModelHealth health;

    private Map<String, Integer> wordVocabulary;
    private double[] wordIdf;
    private Map<String, Integer> charVocabulary;
    private double[] charIdf;
    private double[] intercept;
    private double[][] coefficients;
    private TransactionCategory[] classCategories;

    @Autowired
    public BaselineCategorizationModel(
            @Value("${spendsense.categorization.ml.artifact-path:classpath:model/baseline_model_v1.json}") String artifactPath,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper
    ) {
        this.artifactPath = artifactPath;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.explicitResource = null;
    }

    public BaselineCategorizationModel(Resource resource, ObjectMapper objectMapper) {
        this.artifactPath = resource.getDescription();
        this.resourceLoader = null;
        this.objectMapper = objectMapper;
        this.explicitResource = resource;
        loadArtifact();
    }

    public BaselineCategorizationModel(BaselineModelArtifactData artifactData) {
        this.artifactPath = "in-memory";
        this.resourceLoader = null;
        this.objectMapper = new ObjectMapper();
        this.explicitResource = null;
        initializeFromData(artifactData);
    }

    @PostConstruct
    public void initialize() {
        if (this.explicitResource == null && this.health == null) {
            loadArtifact();
        }
    }

    private void loadArtifact() {
        log.info("Loading baseline categorization model artifact from: {}", artifactPath);
        Resource resource = explicitResource != null ? explicitResource : resourceLoader.getResource(artifactPath);

        if (!resource.exists()) {
            String error = "Model artifact resource not found at " + artifactPath;
            log.warn(error);
            this.health = ModelHealth.unavailable(error);
            return;
        }

        try (InputStream rawIs = resource.getInputStream()) {
            InputStream is = rawIs;
            if (artifactPath.endsWith(".gz")) {
                is = new GZIPInputStream(rawIs);
            }

            BaselineModelArtifactData artifactData = objectMapper.readValue(is, BaselineModelArtifactData.class);
            initializeFromData(artifactData);
            log.info("Baseline categorization model loaded successfully: version={}, classes={}",
                    metadata.modelVersion(), metadata.classes().size());
        } catch (Exception e) {
            String error = "Failed to load/parse model artifact: " + e.getMessage();
            log.error(error, e);
            this.health = ModelHealth.unavailable(error);
        }
    }

    private void initializeFromData(BaselineModelArtifactData artifactData) {
        if (artifactData == null || artifactData.getMetadata() == null) {
            this.health = ModelHealth.unavailable("Invalid artifact: Missing metadata");
            return;
        }

        List<String> rawClasses = artifactData.getMetadata().classes();
        if (rawClasses == null || rawClasses.isEmpty()) {
            this.health = ModelHealth.unavailable("Invalid artifact: Missing class definitions");
            return;
        }

        if (artifactData.getWordVocabulary() == null || artifactData.getWordIdf() == null ||
                artifactData.getCharVocabulary() == null || artifactData.getCharIdf() == null ||
                artifactData.getIntercept() == null || artifactData.getCoefficients() == null) {
            this.health = ModelHealth.unavailable("Invalid artifact: Missing vocabulary or weight parameters");
            return;
        }

        this.metadata = artifactData.getMetadata();
        this.wordVocabulary = Collections.unmodifiableMap(artifactData.getWordVocabulary());
        this.wordIdf = artifactData.getWordIdf();
        this.charVocabulary = Collections.unmodifiableMap(artifactData.getCharVocabulary());
        this.charIdf = artifactData.getCharIdf();
        this.intercept = artifactData.getIntercept();
        this.coefficients = artifactData.getCoefficients();

        int numClasses = rawClasses.size();
        this.classCategories = new TransactionCategory[numClasses];
        for (int i = 0; i < numClasses; i++) {
            this.classCategories[i] = TransactionCategory.valueOf(rawClasses.get(i).trim());
        }

        this.health = ModelHealth.healthy(this.metadata);
    }

    @Override
    public CategorizationPrediction predict(CategorizationInput input) {
        if (health == null || !health.isAvailable()) {
            throw new ModelInferenceException("Categorization model is currently unavailable: " +
                    (health != null ? health.failureReason() : "not initialized"));
        }

        if (input == null) {
            throw new ModelInferenceException("CategorizationInput cannot be null");
        }

        String textToClassify = input.compositeText();
        if (textToClassify == null || textToClassify.isBlank()) {
            textToClassify = input.normalizedMerchant();
        }

        if (textToClassify == null || textToClassify.isBlank()) {
            throw new ModelInferenceException("Cannot categorize transaction: text is empty or blank");
        }

        String normalized = textToClassify.toLowerCase(Locale.ROOT);

        // 1. Extract Word n-grams (1, 2)
        Matcher wordMatcher = WORD_PATTERN.matcher(normalized);
        List<String> tokens = new ArrayList<>();
        while (wordMatcher.find()) {
            tokens.add(wordMatcher.group());
        }

        Map<String, Integer> wordCounts = new HashMap<>();
        for (String token : tokens) {
            wordCounts.put(token, wordCounts.getOrDefault(token, 0) + 1);
        }
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + " " + tokens.get(i + 1);
            wordCounts.put(bigram, wordCounts.getOrDefault(bigram, 0) + 1);
        }

        Map<Integer, Double> wordFeatures = new HashMap<>();
        for (Map.Entry<String, Integer> entry : wordCounts.entrySet()) {
            Integer idx = wordVocabulary.get(entry.getKey());
            if (idx != null) {
                double tf = 1.0 + Math.log(entry.getValue()); // sublinear_tf
                wordFeatures.put(idx, tf * wordIdf[idx]);
            }
        }

        // L2 normalize word feature vector
        double wordNormSq = 0.0;
        for (double val : wordFeatures.values()) {
            wordNormSq += val * val;
        }
        if (wordNormSq > 0.0) {
            double norm = Math.sqrt(wordNormSq);
            for (Map.Entry<Integer, Double> entry : wordFeatures.entrySet()) {
                entry.setValue(entry.getValue() / norm);
            }
        }

        // 2. Extract Char_wb n-grams (3, 5)
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

        Map<Integer, Double> charFeatures = new HashMap<>();
        for (Map.Entry<String, Integer> entry : charNgramCounts.entrySet()) {
            Integer idx = charVocabulary.get(entry.getKey());
            if (idx != null) {
                double tf = 1.0 + Math.log(entry.getValue()); // sublinear_tf
                charFeatures.put(idx, tf * charIdf[idx]);
            }
        }

        // L2 normalize char feature vector
        double charNormSq = 0.0;
        for (double val : charFeatures.values()) {
            charNormSq += val * val;
        }
        if (charNormSq > 0.0) {
            double norm = Math.sqrt(charNormSq);
            for (Map.Entry<Integer, Double> entry : charFeatures.entrySet()) {
                entry.setValue(entry.getValue() / norm);
            }
        }

        // 3. Multinomial Logistic Regression scoring
        int numClasses = classCategories.length;
        double[] z = new double[numClasses];
        System.arraycopy(intercept, 0, z, 0, numClasses);

        // Word features contribution
        for (Map.Entry<Integer, Double> entry : wordFeatures.entrySet()) {
            int featIdx = entry.getKey();
            double val = entry.getValue();
            for (int c = 0; c < numClasses; c++) {
                z[c] += coefficients[c][featIdx] * val;
            }
        }

        // Char features contribution (offset by word vocabulary size)
        int charOffset = wordVocabulary.size();
        for (Map.Entry<Integer, Double> entry : charFeatures.entrySet()) {
            int featIdx = charOffset + entry.getKey();
            double val = entry.getValue();
            for (int c = 0; c < numClasses; c++) {
                z[c] += coefficients[c][featIdx] * val;
            }
        }

        // 4. Softmax with numerical stability
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

        double[] probabilities = new double[numClasses];
        for (int c = 0; c < numClasses; c++) {
            probabilities[c] = expZ[c] / sumExp;
        }

        // 5. Build and sort candidate predictions
        List<CategorizationCandidate> candidates = new ArrayList<>(numClasses);
        for (int c = 0; c < numClasses; c++) {
            candidates.add(new CategorizationCandidate(classCategories[c], probabilities[c]));
        }
        Collections.sort(candidates);

        CategorizationCandidate top = candidates.get(0);
        return new CategorizationPrediction(
                top.category(),
                top.confidence(),
                candidates,
                metadata.modelVersion()
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
}
