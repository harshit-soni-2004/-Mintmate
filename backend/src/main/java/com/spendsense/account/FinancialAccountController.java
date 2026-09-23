package com.spendsense.account;

import com.spendsense.account.dto.FinancialAccountRequest;
import com.spendsense.account.dto.FinancialAccountResponse;
import com.spendsense.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class FinancialAccountController {

    private final FinancialAccountService accountService;

    public FinancialAccountController(FinancialAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FinancialAccountResponse> create(@Valid @RequestBody FinancialAccountRequest request) {
        return ApiResponse.of(accountService.create(request));
    }

    @GetMapping
    public ApiResponse<List<FinancialAccountResponse>> list() {
        return ApiResponse.of(accountService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<FinancialAccountResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(accountService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FinancialAccountResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody FinancialAccountRequest request
    ) {
        return ApiResponse.of(accountService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        accountService.deactivate(id);
    }
}
