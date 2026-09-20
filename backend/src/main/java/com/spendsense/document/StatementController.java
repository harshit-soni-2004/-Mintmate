package com.spendsense.document;

import com.spendsense.common.api.ApiResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.document.dto.DocumentUploadCommand;
import com.spendsense.document.dto.FinancialDocumentResponse;
import com.spendsense.document.dto.StatementUploadResponse;
import com.spendsense.ingestion.service.StatementImportResult;
import com.spendsense.ingestion.service.StatementIngestionService;
import com.spendsense.ingestion.settlement.StatementSettlementRepository;
import com.spendsense.ingestion.settlement.dto.StatementSettlementResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/statements")
public class StatementController {

    private static final long MAX_FILE_SIZE_BYTES = 15L * 1024 * 1024; // 15 MB

    private final FinancialDocumentService financialDocumentService;
    private final StatementIngestionService statementIngestionService;
    private final StatementSettlementRepository settlementRepository;

    public StatementController(
            FinancialDocumentService financialDocumentService,
            StatementIngestionService statementIngestionService,
            StatementSettlementRepository settlementRepository
    ) {
        this.financialDocumentService = financialDocumentService;
        this.statementIngestionService = statementIngestionService;
        this.settlementRepository = settlementRepository;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StatementUploadResponse> uploadAndIngest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "accountType", defaultValue = "SAVINGS") DocumentAccountType accountType,
            @RequestParam(value = "institutionName", required = false) String institutionName,
            @RequestParam(value = "accountNumberLast4", required = false) String accountNumberLast4,
            @RequestParam(value = "statementStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementStartDate,
            @RequestParam(value = "statementEndDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statementEndDate
    ) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "Uploaded statement file cannot be empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "File size exceeds the 15 MB limit");
        }

        FinancialDocumentResponse docResponse;
        try {
            DocumentUploadCommand command = new DocumentUploadCommand(
                    file.getOriginalFilename(),
                    accountType,
                    institutionName,
                    accountNumberLast4,
                    statementStartDate,
                    statementEndDate,
                    file.getInputStream(),
                    file.getSize()
            );
            docResponse = financialDocumentService.upload(command);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_READ_ERROR", "Failed to read uploaded file stream");
        }

        StatementImportResult importResult = statementIngestionService.ingestDocument(docResponse.id());
        FinancialDocumentResponse updatedDoc = financialDocumentService.get(docResponse.id());

        return ApiResponse.of(new StatementUploadResponse(updatedDoc, importResult));
    }

    @PostMapping("/{id}/ingest")
    public ApiResponse<StatementUploadResponse> ingest(@PathVariable UUID id) {
        StatementImportResult result = statementIngestionService.ingestDocument(id);
        FinancialDocumentResponse updatedDoc = financialDocumentService.get(id);
        return ApiResponse.of(new StatementUploadResponse(updatedDoc, result));
    }

    @GetMapping
    public ApiResponse<List<FinancialDocumentResponse>> list() {
        return ApiResponse.of(financialDocumentService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<FinancialDocumentResponse> get(@PathVariable UUID id) {
        return ApiResponse.of(financialDocumentService.get(id));
    }

    @GetMapping("/{id}/settlements")
    public ApiResponse<List<StatementSettlementResponse>> listSettlements(@PathVariable UUID id) {
        // Enforce ownership check to prevent cross-user IDOR access
        financialDocumentService.get(id);
        List<StatementSettlementResponse> settlements = settlementRepository.findByDocumentId(id)
                .stream()
                .map(StatementSettlementResponse::from)
                .toList();
        return ApiResponse.of(settlements);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        financialDocumentService.delete(id);
    }
}
