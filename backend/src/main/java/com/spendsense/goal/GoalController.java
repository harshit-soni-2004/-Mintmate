package com.spendsense.goal;

import com.spendsense.common.api.ApiResponse;
import com.spendsense.goal.dto.GoalContributionRequest;
import com.spendsense.goal.dto.GoalRequest;
import com.spendsense.goal.dto.GoalResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GoalResponse> create(@Valid @RequestBody GoalRequest request) {
        return ApiResponse.of(goalService.create(request));
    }

    @GetMapping
    public ApiResponse<List<GoalResponse>> list() {
        return ApiResponse.of(goalService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<GoalResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(goalService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<GoalResponse> update(@PathVariable UUID id, @Valid @RequestBody GoalRequest request) {
        return ApiResponse.of(goalService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        goalService.delete(id);
    }

    @PatchMapping("/{id}/contribution")
    public ApiResponse<GoalResponse> addContribution(
            @PathVariable UUID id,
            @Valid @RequestBody GoalContributionRequest request) {
        return ApiResponse.of(goalService.addContribution(id, request));
    }
}
