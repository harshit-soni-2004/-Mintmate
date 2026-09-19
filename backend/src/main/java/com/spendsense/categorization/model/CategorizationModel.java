package com.spendsense.categorization.model;

/**
 * Clean application-level abstraction for ML transaction categorization inference.
 * <p>
 * Decouples the application from all underlying training frameworks (scikit-learn,
 * PyTorch, ONNX Runtime, joblib serialization, or remote endpoints).
 * Allows future drop-in replacement with embeddings, LLMs, or transformer models
 * without altering any upper categorization business logic.
 */
public interface CategorizationModel {

    /**
     * Scores a transaction input and returns the top predicted category along with
     * confidence and ranked alternative candidates.
     *
     * @param input transaction categorization input
     * @return model prediction with probability distribution
     * @throws ModelInferenceException if model is unavailable or input cannot be scored
     */
    CategorizationPrediction predict(CategorizationInput input);

    /**
     * Returns metadata for this model (version, training timestamp, dataset version, algorithm).
     */
    ModelMetadata getModelMetadata();

    /**
     * Returns operational health status of the model artifact.
     */
    ModelHealth getModelHealth();
}
