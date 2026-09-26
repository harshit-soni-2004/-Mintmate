package com.spendsense.assistant.model.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.config.AssistantProperties;
import com.spendsense.assistant.model.*;
import com.spendsense.assistant.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

@Component
public class GeminiModelGateway implements ModelGateway {

    private static final Logger log = LoggerFactory.getLogger(GeminiModelGateway.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssistantProperties properties;
    private final MockModelGateway mockFallback;
    private final RestClient restClient;

    public GeminiModelGateway(AssistantProperties properties, MockModelGateway mockFallback) {
        this.properties = properties;
        this.mockFallback = mockFallback;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = properties.getTimeoutMs() > 0 ? properties.getTimeoutMs() : 30000;
        requestFactory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public AssistantResponse generateResponse(AssistantConversationContext context, List<ToolDefinition> tools) {
        if (!properties.hasApiKey()) {
            log.debug("No API key configured for Gemini; delegating to deterministic MockModelGateway");
            return mockFallback.generateResponse(context, tools);
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
        } catch (Exception e) {
            log.warn("Gemini API call failed, falling back to mock: {}", e.getClass().getSimpleName());
            return mockFallback.generateResponse(context, tools);
        }
    }

    private Map<String, Object> buildGeminiRequest(AssistantConversationContext context, List<ToolDefinition> tools) {
        Map<String, Object> body = new HashMap<>();

        if (context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", context.systemPrompt()))
            ));
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (AssistantMessage msg : context.messages()) {
            String role = msg.role() == AssistantMessageRole.ASSISTANT ? "model" : "user";
            List<Map<String, Object>> parts = new ArrayList<>();

            if (msg.role() == AssistantMessageRole.TOOL) {
                parts.add(Map.of(
                        "functionResponse", Map.of(
                                "name", msg.toolName(),
                                "response", Map.of("content", msg.content())
                        )
                ));
            } else if (msg.content() != null) {
                parts.add(Map.of("text", msg.content()));
            }

            contents.add(Map.of("role", role, "parts", parts));
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

    private AssistantResponse parseGeminiResponse(String responseJson) {
        try {
            Map<String, Object> map = OBJECT_MAPPER.readValue(responseJson, new TypeReference<>() {});
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) map.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return AssistantResponse.text("I could not generate an answer at this time.");
            }

            Map<String, Object> firstCandidate = candidates.getFirst();
            Map<String, Object> content = (Map<String, Object>) firstCandidate.get("content");
            if (content == null || !content.containsKey("parts")) {
                return AssistantResponse.text("I could not generate an answer at this time.");
            }
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return AssistantResponse.text("I could not generate an answer at this time.");
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

            return AssistantResponse.text(textContent.toString());
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage(), e);
            return AssistantResponse.text("Error processing response from assistant.");
        }
    }
}
