package com.spendsense.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.assistant.action.PendingActionService;
import com.spendsense.assistant.action.PendingActionStatus;
import com.spendsense.assistant.dto.*;
import com.spendsense.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AssistantControllerTest {

    @Mock
    private AssistantOrchestrator orchestrator;

    @Mock
    private PendingActionService pendingActionService;

    @Mock
    private com.spendsense.assistant.proactive.ProactiveInsightService proactiveInsightService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(orchestrator, pendingActionService, proactiveInsightService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Blank message to /api/v1/assistant/chat should return 400 validation error")
    void chat_blankMessage_returns400() throws Exception {
        AssistantChatRequest request = new AssistantChatRequest("", List.of());

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Oversized message (> 2000 chars) should return 400 validation error")
    void chat_oversizedMessage_returns400() throws Exception {
        String longMessage = "a".repeat(2001);
        AssistantChatRequest request = new AssistantChatRequest(longMessage, List.of());

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Excessive conversation history (> 30 messages) should return 400 validation error")
    void chat_excessiveHistory_returns400() throws Exception {
        List<ChatMessageDto> excessiveHistory = java.util.stream.IntStream.range(0, 31)
                .mapToObj(i -> new ChatMessageDto("user", "Hello " + i))
                .toList();
        AssistantChatRequest request = new AssistantChatRequest("Valid message", excessiveHistory);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Valid chat request returns 200 with structured AssistantChatResponse")
    void chat_validRequest_returns200() throws Exception {
        AssistantChatRequest request = new AssistantChatRequest("How much did I spend on food?", List.of());

        AssistantChatResponse response = AssistantChatResponse.text(
                "Your spending on Food was ₹9,617 last month.",
                List.of(new ToolCallSummaryDto("get_spending_by_category", "Spending on Food was ₹9,617."))
        );

        when(orchestrator.processChat(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("Your spending on Food was ₹9,617 last month."))
                .andExpect(jsonPath("$.data.requiresConfirmation").value(false))
                .andExpect(jsonPath("$.data.executedTools[0].toolName").value("get_spending_by_category"));
    }

    @Test
    @DisplayName("Confirm action endpoint executes confirmed mutation")
    void confirmAction_returns200() throws Exception {
        UUID actionId = UUID.randomUUID();
        PendingActionResultDto resultDto = new PendingActionResultDto(
                actionId,
                "update_transaction_category",
                PendingActionStatus.EXECUTED,
                "Changed category to Food",
                null,
                Instant.now()
        );

        when(pendingActionService.confirmAndExecute(eq(actionId))).thenReturn(resultDto);

        mockMvc.perform(post("/api/v1/assistant/actions/" + actionId + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actionId").value(actionId.toString()))
                .andExpect(jsonPath("$.data.status").value("EXECUTED"));
    }

    @Test
    @DisplayName("Cancel action endpoint cancels pending mutation")
    void cancelAction_returns200() throws Exception {
        UUID actionId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/assistant/actions/" + actionId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Action successfully cancelled"));

        verify(pendingActionService).cancelAction(actionId);
    }
}
