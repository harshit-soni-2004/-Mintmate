package com.spendsense.debt;

import com.spendsense.common.api.ApiResponse;
import com.spendsense.debt.dto.CreateDebtRequest;
import com.spendsense.debt.dto.DebtCandidateTransactionDto;
import com.spendsense.debt.dto.DebtDto;
import com.spendsense.debt.dto.DebtSummaryDto;
import com.spendsense.debt.dto.SettleDebtRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/debts")
public class DebtController {

    private final DebtService debtService;

    public DebtController(DebtService debtService) {
        this.debtService = debtService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DebtDto>> createDebt(@Valid @RequestBody CreateDebtRequest request) {
        DebtDto created = debtService.createDebt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DebtDto>>> getDebts(
            @RequestParam(name = "status", required = false) DebtStatus status) {
        List<DebtDto> debts = debtService.getDebts(status);
        return ResponseEntity.ok(ApiResponse.of(debts));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DebtSummaryDto>> getDebtSummary() {
        DebtSummaryDto summary = debtService.getDebtSummary();
        return ResponseEntity.ok(ApiResponse.of(summary));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DebtDto>> getDebt(@PathVariable("id") UUID id) {
        DebtDto debt = debtService.getDebt(id);
        return ResponseEntity.ok(ApiResponse.of(debt));
    }

    @GetMapping("/{id}/candidates")
    public ResponseEntity<ApiResponse<List<DebtCandidateTransactionDto>>> getCandidateTransactions(
            @PathVariable("id") UUID id) {
        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(id);
        return ResponseEntity.ok(ApiResponse.of(candidates));
    }

    @PostMapping("/{id}/settle")
    public ResponseEntity<ApiResponse<DebtDto>> settleDebt(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) SettleDebtRequest request) {
        DebtDto settled = debtService.settleDebt(id, request);
        return ResponseEntity.ok(ApiResponse.of(settled));
    }

    @PostMapping("/{id}/record-payment-and-settle")
    public ResponseEntity<ApiResponse<DebtDto>> recordPaymentAndSettle(
            @PathVariable("id") UUID id,
            @Valid @RequestBody com.spendsense.debt.dto.RecordPaymentAndSettleRequest request) {
        DebtDto settled = debtService.recordPaymentAndSettle(id, request);
        return ResponseEntity.ok(ApiResponse.of(settled));
    }

    @PostMapping("/{id}/link-transaction")
    public ResponseEntity<ApiResponse<DebtDto>> linkTransactionToSettledDebt(
            @PathVariable("id") UUID id,
            @RequestBody SettleDebtRequest request) {
        DebtDto linked = debtService.linkTransactionToSettledDebt(id, request);
        return ResponseEntity.ok(ApiResponse.of(linked));
    }

    @PostMapping("/{id}/candidates/{transactionId}/dismiss")
    public ResponseEntity<ApiResponse<Void>> dismissCandidate(
            @PathVariable("id") UUID id,
            @PathVariable("transactionId") UUID transactionId) {
        debtService.dismissCandidate(id, transactionId);
        return ResponseEntity.ok(ApiResponse.of(null));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<DebtDto>> cancelDebt(@PathVariable("id") UUID id) {
        DebtDto cancelled = debtService.cancelDebt(id);
        return ResponseEntity.ok(ApiResponse.of(cancelled));
    }
}
