package com.spendsense.categorization.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Data Transfer Object for deserializing the Phase 6F hybrid (MiniLM Dense + TF-IDF Sparse) artifact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HybridModelArtifactData {

    @JsonProperty("metadata")
    private ModelMetadata metadata;

    @JsonProperty("embedding_dim")
    private int embeddingDim;

    @JsonProperty("word_vocabulary")
    private Map<String, Integer> wordVocabulary;

    @JsonProperty("word_idf")
    private double[] wordIdf;

    @JsonProperty("char_vocabulary")
    private Map<String, Integer> charVocabulary;

    @JsonProperty("char_idf")
    private double[] charIdf;

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

    public int getEmbeddingDim() {
        return embeddingDim;
    }

    public void setEmbeddingDim(int embeddingDim) {
        this.embeddingDim = embeddingDim;
    }

    public Map<String, Integer> getWordVocabulary() {
        return wordVocabulary;
    }

    public void setWordVocabulary(Map<String, Integer> wordVocabulary) {
        this.wordVocabulary = wordVocabulary;
    }

    public double[] getWordIdf() {
        return wordIdf;
    }

    public void setWordIdf(double[] wordIdf) {
        this.wordIdf = wordIdf;
    }

    public Map<String, Integer> getCharVocabulary() {
        return charVocabulary;
    }

    public void setCharVocabulary(Map<String, Integer> charVocabulary) {
        this.charVocabulary = charVocabulary;
    }

    public double[] getCharIdf() {
        return charIdf;
    }

    public void setCharIdf(double[] charIdf) {
        this.charIdf = charIdf;
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
