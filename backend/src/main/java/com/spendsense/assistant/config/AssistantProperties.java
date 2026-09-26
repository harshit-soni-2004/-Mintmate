package com.spendsense.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spendsense.assistant")
public class AssistantProperties {

    /**
     * Active provider: "gemini", "mock", etc.
     */
    private String provider = "mock";

    /**
     * Model name/version (e.g. gemini-2.5-flash).
     */
    private String model = "gemini-2.5-flash";

    /**
     * API key for the model provider.
     */
    private String apiKey = "";

    /**
     * Base URL for the provider endpoint.
     */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /**
     * HTTP timeout in milliseconds.
     */
    private int timeoutMs = 30000;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
