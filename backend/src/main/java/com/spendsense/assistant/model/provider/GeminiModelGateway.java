package com.spendsense.assistant.model.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.config.AssistantProperties;
import com.spendsense.assistant.model.*;
import com.spendsense.assistant.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

@Component
public class GeminiModelGateway implements ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(GeminiModelGateway.class);
    private static final String DEFAULT_USER_FACING_ERROR = "Something went wrong while processing that. Please try again.";

    private final ObjectMapper objectMapper;
    private final AssistantProperties properties;
    private final MockModelGateway mockFallback;
    private final RestClient restClient;

    @Autowired
    public GeminiModelGateway(AssistantProperties properties, MockModelGateway mockFallback, ObjectMapper objectMapper) {
        this.properties = properties;
        this.mockFallback = mockFallback;
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 30000;
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public GeminiModelGateway(AssistantProperties properties, MockModelGateway mockFallback) {
        this(properties, mockFallback, createDefaultObjectMapper());
    }

    public GeminiModelGateway(AssistantProperties properties, MockModelGateway mockFallback, RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.mockFallback = mockFallback;
        this.restClient = restClient;
        this.objectMapper = objectMapper != null ? objectMapper : createDefaultObjectMapper();
    }

    private static ObjectMapper createDefaultObjectMapper() {
        return new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public AssistantResponse generateResponse(AssistantConversationContext context, List<ToolDefinition> tools) {
        if (!properties.hasApiKey()) {
            log.debug("No API key configured for Gemini; delegating to deterministic MockModelGateway");
            return fallbackSafely(context, tools);
        }

        try {
            Map<String, Object> requestBody = buildGeminiRequest(context, tools);
            String url = String.format("/v1beta/models/%s:generateContent", properties.getModel());

            String responseJson = restClient.post()
                    .uri(url)
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseGeminiResponse(responseJson);

        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Gemini rate limit exceeded (HTTP 429). Falling back gracefully.");
            return fallbackSafely(context, tools);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.warn("Gemini authentication/authorization failed (HTTP {}). Verify configured credentials.", e.getStatusCode().value());
            return fallbackSafely(context, tools);
        } catch (HttpStatusCodeException e) {
            log.warn("Gemini API HTTP failure [status={}]: {}", e.getStatusCode().value(), sanitizeErrorMessage(e.getStatusText()));
            return fallbackSafely(context, tools);
        } catch (ResourceAccessException e) {
            log.warn("Gemini connection or timeout error: {}", e.getMessage() != null ? e.getClass().getSimpleName() : "Timeout");
            return fallbackSafely(context, tools);
        } catch (Exception e) {
            log.warn("Gemini unexpected invocation error: {}", e.getClass().getSimpleName());
            return fallbackSafely(context, tools);
        }
    }

    private AssistantResponse fallbackSafely(AssistantConversationContext context, List<ToolDefinition> tools) {
        if (mockFallback != null) {
            try {
                return mockFallback.generateResponse(context, tools);
            } catch (Exception ex) {
                log.error("Fallback to MockModelGateway failed: {}", ex.getMessage());
            }
        }
        return AssistantResponse.text(DEFAULT_USER_FACING_ERROR);
    }

    public Map<String, Object> buildGeminiRequest(AssistantConversationContext context, List<ToolDefinition> tools) {
        Map<String, Object> body = new HashMap<>();

        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", context.systemPrompt()))
            ));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (AssistantMessage msg : context.messages()) {
            if (msg.role() == AssistantMessageRole.SYSTEM) {
                continue;
            }

            String role = msg.role() == AssistantMessageRole.ASSISTANT ? "model" : "user";
            List<Map<String, Object>> parts = new ArrayList<>();

            if (msg.role() == AssistantMessageRole.TOOL) {
                Map<String, Object> respContent = new HashMap<>();
                respContent.put("content", msg.content() != null ? msg.content() : "");
                parts.add(Map.of(
                        "functionResponse", Map.of(
                                "name", msg.toolName() != null ? msg.toolName() : "tool",
                                "response", respContent
                        )
                ));
            } else if (msg.role() == AssistantMessageRole.ASSISTANT) {
                if (msg.content() != null && !msg.content().isBlank()) {
                    parts.add(Map.of("text", msg.content()));
                }
                if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                    for (ToolCallRequest tcr : msg.toolCalls()) {
                        parts.add(Map.of(
                                "functionCall", Map.of(
                                        "name", tcr.name(),
                                        "args", tcr.arguments() != null ? tcr.arguments() : Map.of()
                                )
                        ));
                    }
                }
            } else {
                if (msg.content() != null && !msg.content().isBlank()) {
                    parts.add(Map.of("text", msg.content()));
                }
            }

            if (!parts.isEmpty()) {
                contents.add(Map.of("role", role, "parts", parts));
            }
        }
        body.put("contents", contents);

        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> functionDeclarations = tools.stream()
                    .map(t -> Map.of(
                            "name", (Object) t.name(),
                            "description", t.description(),
                            "parameters", t.parameters() != null ? t.parameters() : Map.of("type", "object")
                    ))
                    .toList();
            body.put("tools", List.of(Map.of("functionDeclarations", functionDeclarations)));
        }

        return body;
    }

    public AssistantResponse parseGeminiResponse(String responseJson) {
        try {
            Map<String, Object> map = objectMapper.readValue(responseJson, new TypeReference<>() {});

            if (map.containsKey("promptFeedback")) {
                Map<String, Object> pf = (Map<String, Object>) map.get("promptFeedback");
                if (pf != null && pf.containsKey("blockReason")) {
                    log.warn("Gemini prompt blocked due to: {}", pf.get("blockReason"));
                    return AssistantResponse.text("I am unable to answer this question due to safety policies. Please ask a personal finance related question.");
                }
            }

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) map.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return AssistantResponse.text(DEFAULT_USER_FACING_ERROR);
            }

            Map<String, Object> firstCandidate = candidates.getFirst();

            Object finishReasonObj = firstCandidate.get("finishReason");
            if (finishReasonObj != null) {
                String finishReason = finishReasonObj.toString();
                if ("SAFETY".equalsIgnoreCase(finishReason) || "BLOCKLIST".equalsIgnoreCase(finishReason) || "RECITATION".equalsIgnoreCase(finishReason)) {
                    log.warn("Gemini candidate blocked by content safety: {}", finishReason);
                    return AssistantResponse.text("I am unable to answer this question due to safety policies. Please ask a personal finance related question.");
                }
            }

            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            if (content == null || !content.containsKey("parts")) {
                return AssistantResponse.text(DEFAULT_USER_FACING_ERROR);
            }
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return AssistantResponse.text(DEFAULT_USER_FACING_ERROR);
            }

            List<ToolCallRequest> toolCalls = new ArrayList<>();
            StringBuilder textContent = new StringBuilder();

            for (Map<String, Object> part : parts) {
                if (part.containsKey("functionCall")) {
                    Map<String, Object> fc = (Map<String, Object>) part.get("functionCall");
                    String name = (String) fc.get("name");
                    Map<String, Object> args = (Map<String, Object>) fc.get("args");
                    toolCalls.add(new ToolCallRequest(UUID.randomUUID().toString(), name, args != null ? args : Map.of()));
                } else if (part.containsKey("text")) {
                    textContent.append((String) part.get("text"));
                }
            }

            if (!toolCalls.isEmpty()) {
                return AssistantResponse.ofToolCalls(toolCalls);
            }

            String answer = textContent.toString().trim();
            if (answer.isEmpty()) {
                return AssistantResponse.text(DEFAULT_USER_FACING_ERROR);
            }
            return AssistantResponse.text(answer);

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return AssistantResponse.text(DEFAULT_USER_FACING_ERROR);
        }
    }

    private static String sanitizeErrorMessage(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("(?i)(key|token|auth)=[^&\\s]+", "$1=***");
    }
}
