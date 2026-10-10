package com.spendsense.assistant.model.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.config.AssistantProperties;
import com.spendsense.assistant.model.*;
import com.spendsense.assistant.tool.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiModelGatewayTest {

    @Mock private MockModelGateway mockFallback;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock private RestClient.RequestBodySpec requestBodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private AssistantProperties properties;
    private ObjectMapper objectMapper;
    private GeminiModelGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new AssistantProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("gemini-2.5-flash");
        objectMapper = new ObjectMapper();
        gateway = new GeminiModelGateway(properties, mockFallback, restClient, objectMapper);
    }

    @Test
    @DisplayName("Should delegate to MockModelGateway if no API key is configured")
    void shouldDelegateToMockWhenNoApiKey() {
        properties.setApiKey("");
        AssistantConversationContext ctx = new AssistantConversationContext("sys", List.of(AssistantMessage.user("Hello")));
        when(mockFallback.generateResponse(any(), any())).thenReturn(AssistantResponse.text("Mock response"));

        AssistantResponse response = gateway.generateResponse(ctx, List.of());

        assertThat(response.content()).isEqualTo("Mock response");
        verify(mockFallback).generateResponse(ctx, List.of());
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("Should fall back cleanly on HTTP 429 Too Many Requests without leaking secrets")
    void shouldHandleRateLimitGracefully() {
        AssistantConversationContext ctx = new AssistantConversationContext("sys", List.of(AssistantMessage.user("Hello")));
        when(mockFallback.generateResponse(any(), any())).thenReturn(AssistantResponse.text("Fallback response"));

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", HttpHeaders.EMPTY, new byte[0], null));

        AssistantResponse response = gateway.generateResponse(ctx, List.of());

        assertThat(response.content()).isEqualTo("Fallback response");
        verify(mockFallback).generateResponse(ctx, List.of());
    }

    @Test
    @DisplayName("Should fall back cleanly on timeout / network failure")
    void shouldHandleTimeoutGracefully() {
        AssistantConversationContext ctx = new AssistantConversationContext("sys", List.of(AssistantMessage.user("Hello")));
        when(mockFallback.generateResponse(any(), any())).thenReturn(AssistantResponse.text("Network fallback"));

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(new ResourceAccessException("Read timed out"));

        AssistantResponse response = gateway.generateResponse(ctx, List.of());

        assertThat(response.content()).isEqualTo("Network fallback");
    }

    @Test
    @DisplayName("Should return safe refusal when Gemini blocks content for safety")
    void shouldHandleSafetyBlockedResponse() {
        String safetyResponseJson = """
                {
                  "candidates": [
                    {
                      "finishReason": "SAFETY"
                    }
                  ]
                }
                """;
        AssistantResponse response = gateway.parseGeminiResponse(safetyResponseJson);

        assertThat(response.content()).contains("safety policies");
        assertThat(response.hasToolCalls()).isFalse();
    }

    @Test
    @DisplayName("Should parse tool calls correctly from Gemini response")
    void shouldParseToolCalls() {
        String responseJson = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {
                            "functionCall": {
                              "name": "get_spending_summary",
                              "args": {
                                "startDate": "2026-08-01",
                                "endDate": "2026-08-31"
                              }
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;
        AssistantResponse response = gateway.parseGeminiResponse(responseJson);

        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls()).hasSize(1);
        ToolCallRequest tcr = response.toolCalls().getFirst();
        assertThat(tcr.name()).isEqualTo("get_spending_summary");
        assertThat(tcr.arguments().get("startDate")).isEqualTo("2026-08-01");
    }

    @Test
    @DisplayName("Should serialize multi-turn conversation with functionCall and functionResponse")
    void shouldSerializeMultiTurnWithToolCalling() {
        AssistantMessage userMsg = AssistantMessage.user("How much did I spend?");
        AssistantMessage modelMsg = AssistantMessage.assistantWithToolCalls(
                null,
                List.of(new ToolCallRequest("c1", "get_spending_summary", Map.of("startDate", "2026-08-01")))
        );
        AssistantMessage toolMsg = AssistantMessage.tool("c1", "get_spending_summary", "{\"totalExpenses\": 35000}");

        AssistantConversationContext ctx = new AssistantConversationContext(
                "You are an assistant",
                List.of(userMsg, modelMsg, toolMsg)
        );

        Map<String, Object> req = gateway.buildGeminiRequest(ctx, List.of());
        List<Map<String, Object>> contents = (List<Map<String, Object>>) req.get("contents");

        assertThat(contents).hasSize(3);

        // Turn 1: user
        assertThat(contents.get(0).get("role")).isEqualTo("user");

        // Turn 2: model with functionCall
        assertThat(contents.get(1).get("role")).isEqualTo("model");
        List<Map<String, Object>> parts2 = (List<Map<String, Object>>) contents.get(1).get("parts");
        assertThat(parts2.getFirst()).containsKey("functionCall");

        // Turn 3: functionResponse
        List<Map<String, Object>> parts3 = (List<Map<String, Object>>) contents.get(2).get("parts");
        assertThat(parts3.getFirst()).containsKey("functionResponse");
    }
}
