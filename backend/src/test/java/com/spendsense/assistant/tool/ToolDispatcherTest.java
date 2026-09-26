package com.spendsense.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.user.User;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolDispatcherTest {

    @Mock private ToolRegistry toolRegistry;
    @Mock private CurrentUser currentUser;
    @Mock private FinancialTool<DummyInput, String> dummyTool;

    private ToolDispatcher dispatcher;
    private SpendSenseUserPrincipal principal;

    public record DummyInput(String requiredField) {}

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        dispatcher = new ToolDispatcher(toolRegistry, currentUser, objectMapper, validator);

        User user = new User("user@example.com", "hash", "Test User", "Asia/Kolkata");
        principal = new SpendSenseUserPrincipal(user);
    }

    @Test
    @DisplayName("Should reject unknown tools safely")
    void shouldRejectUnknownTool() {
        when(toolRegistry.getTool("unregistered_tool")).thenReturn(Optional.empty());

        ToolExecutionResult<?> result = dispatcher.dispatch("unregistered_tool", Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Unknown tool: 'unregistered_tool'");
    }

    @Test
    @DisplayName("Should reject execution when authentication is missing")
    void shouldRejectUnauthenticatedExecution() {
        when(toolRegistry.getTool("dummy_tool")).thenReturn(Optional.of(dummyTool));
        when(currentUser.require()).thenThrow(new IllegalStateException("Unauthenticated"));

        ToolExecutionResult<?> result = dispatcher.dispatch("dummy_tool", Map.of("requiredField", "val"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Authentication required");
        verify(dummyTool, never()).execute(any(), any());
    }

    @Test
    @DisplayName("Should dispatch valid typed tool successfully using CurrentUser")
    void shouldDispatchValidTool() {
        when(toolRegistry.getTool("dummy_tool")).thenReturn(Optional.of(dummyTool));
        doReturn(DummyInput.class).when(dummyTool).getInputType();
        when(currentUser.require()).thenReturn(principal);

        ToolResponseEnvelope<String> envelope = ToolResponseEnvelope.of("success-data", "Summary message");
        when(dummyTool.execute(eq(new DummyInput("test-value")), eq(principal)))
                .thenReturn(ToolExecutionResult.success(envelope));

        ToolExecutionResult<?> result = dispatcher.dispatch("dummy_tool", Map.of("requiredField", "test-value"));

        assertThat(result.success()).isTrue();
        assertThat(result.response()).isNotNull();
        assertThat(result.response().data()).isEqualTo("success-data");
        assertThat(result.response().summaryText()).isEqualTo("Summary message");
    }
}
