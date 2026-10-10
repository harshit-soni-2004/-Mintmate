package com.spendsense.assistant;

import com.spendsense.assistant.action.PendingActionService;
import com.spendsense.assistant.dto.AssistantChatRequest;
import com.spendsense.assistant.dto.AssistantChatResponse;
import com.spendsense.assistant.dto.PendingActionResultDto;
import com.spendsense.assistant.proactive.ProactiveInsightDto;
import com.spendsense.assistant.proactive.ProactiveInsightService;
import com.spendsense.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantOrchestrator orchestrator;
    private final PendingActionService pendingActionService;
    private final ProactiveInsightService proactiveInsightService;

    public AssistantController(
            AssistantOrchestrator orchestrator,
            PendingActionService pendingActionService,
            ProactiveInsightService proactiveInsightService
    ) {
        this.orchestrator = orchestrator;
        this.pendingActionService = pendingActionService;
        this.proactiveInsightService = proactiveInsightService;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest request) {
        AssistantChatResponse response = orchestrator.processChat(request);
        return ApiResponse.of(response);
    }

    @PostMapping(value = "/actions/{id}/confirm", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PendingActionResultDto> confirmAction(@PathVariable("id") UUID actionId) {
        PendingActionResultDto result = pendingActionService.confirmAndExecute(actionId);
        return ApiResponse.of(result);
    }

    @PostMapping(value = "/actions/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, String>> cancelAction(@PathVariable("id") UUID actionId) {
        pendingActionService.cancelAction(actionId);
        return ApiResponse.of(Map.of("message", "Action successfully cancelled"));
    }

    @GetMapping(value = "/proactive-insights", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<List<ProactiveInsightDto>> getProactiveInsights() {
        List<ProactiveInsightDto> insights = proactiveInsightService.getActiveInsights();
        return ApiResponse.of(insights);
    }

    @RequestMapping(value = "/proactive-insights/{id}/dismiss", method = {RequestMethod.POST, RequestMethod.DELETE}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, String>> dismissProactiveInsight(@PathVariable("id") UUID insightId) {
        proactiveInsightService.dismissInsight(insightId);
        return ApiResponse.of(Map.of("message", "Insight successfully dismissed"));
    }
}
