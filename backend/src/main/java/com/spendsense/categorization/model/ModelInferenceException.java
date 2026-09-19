package com.spendsense.categorization.model;

/**
 * Exception thrown when model inference fails or model is invoked in an unavailable state.
 */
public class ModelInferenceException extends RuntimeException {
    public ModelInferenceException(String message) {
        super(message);
    }

    public ModelInferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
