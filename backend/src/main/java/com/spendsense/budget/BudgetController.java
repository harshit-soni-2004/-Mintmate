package com.spendsense.budget;

import com.spendsense.budget.dto.BudgetRequest;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BudgetResponse> create(@Valid @RequestBody BudgetRequest request) {
        return ApiResponse.of(budgetService.create(request));
    }

    @GetMapping
    public ApiResponse<List<BudgetResponse>> list(
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.of(budgetService.list(yearMonth, fromDate, toDate));
    }

    @GetMapping("/summary")
    public ApiResponse<BudgetSummaryResponse> summary(
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.of(budgetService.getSummary(yearMonth, fromDate, toDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<BudgetResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(budgetService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<BudgetResponse> update(@PathVariable UUID id, @Valid @RequestBody BudgetRequest request) {
        return ApiResponse.of(budgetService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        budgetService.delete(id);
    }
}
