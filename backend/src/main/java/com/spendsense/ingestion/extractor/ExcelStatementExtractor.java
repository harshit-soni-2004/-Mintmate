package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.StatementNormalizer;
import com.spendsense.ingestion.StatementNormalizer.ParsedAmount;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Excel statement extractor supporting both .xlsx and .xls formats, multi-sheet inspection,
 * native date cell handling, formula evaluation, dynamic header discovery, and summary row filtering.
 */
@Component
public class ExcelStatementExtractor implements DocumentExtractor {

    private final StatementNormalizer statementNormalizer;
    private final CandidateValidator candidateValidator;
    private final DataFormatter dataFormatter;

    public ExcelStatementExtractor(StatementNormalizer statementNormalizer, CandidateValidator candidateValidator) {
        this.statementNormalizer = statementNormalizer;
        this.candidateValidator = candidateValidator;
        this.dataFormatter = new DataFormatter();
    }

    @Override
    public boolean supports(DocumentFileType fileType) {
        return fileType == DocumentFileType.XLSX;
    }

    @Override
    public List<TransactionCandidate> extract(
        UUID documentId,
        DocumentAccountType accountType,
        InputStream stream
    ) throws DocumentExtractionException {
        if (stream == null) {
            throw new DocumentExtractionException(DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT, "Document stream is null");
        }

        Workbook workbook;
        try {
            workbook = WorkbookFactory.create(stream);
        } catch (Exception e) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.PARSING_ERROR,
                "Failed to parse Excel workbook: " + e.getMessage(),
                e
            );
        }

        try {
            if (workbook.getNumberOfSheets() == 0) {
                throw new DocumentExtractionException(DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT, "Workbook has no sheets");
            }

            // 1. Inspect sheets to find candidate transaction sheets
            List<SheetCandidate> candidateSheets = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                SheetCandidate candidate = inspectSheetForHeader(sheet);
                if (candidate != null) {
                    candidateSheets.add(candidate);
                }
            }

            if (candidateSheets.isEmpty()) {
                throw new DocumentExtractionException(
                    DocumentExtractionException.ErrorCode.NO_HEADER_FOUND,
                    "No worksheet contains a valid transaction table header"
                );
            }

            // 2. Select the best sheet
            SheetCandidate selectedSheetCandidate = selectBestSheet(candidateSheets);
            Sheet sheet = selectedSheetCandidate.sheet;
            HeaderMapping mapping = selectedSheetCandidate.mapping;
            int headerRowIndex = selectedSheetCandidate.headerRowIndex;

            // 3. Extract transaction rows
            List<TransactionCandidate> candidates = new ArrayList<>();
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                if (isSummaryOrFooterRow(row, mapping)) {
                    continue;
                }

                Map<String, String> cellValues = new LinkedHashMap<>();
                for (int col = 0; col < row.getLastCellNum(); col++) {
                    Cell cell = row.getCell(col);
                    cellValues.put("col_" + col, getCellStringValue(cell));
                }
                RawStatementRow rawRow = new RawStatementRow(i + 1, cellValues, row.toString());

                TransactionCandidate candidate = mapRowToCandidate(rawRow, row, mapping, documentId, accountType);
                if (candidate != null) {
                    TransactionCandidate validated = candidate.withValidationResult(candidateValidator.validate(candidate));
                    candidates.add(validated);
                }
            }

            return candidates;
        } finally {
            try {
                workbook.close();
            } catch (Exception ignored) {
            }
        }
    }

    private SheetCandidate inspectSheetForHeader(Sheet sheet) {
        int maxScan = Math.min(sheet.getLastRowNum(), 50);
        for (int r = 0; r <= maxScan; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            HeaderMapping mapping = detectHeaderMapping(row);
            if (mapping != null) {
                return new SheetCandidate(sheet, mapping, r);
            }
        }
        return null;
    }

    private SheetCandidate selectBestSheet(List<SheetCandidate> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Prioritize by sheet name matching statement keywords
        List<String> keywords = List.of("transaction", "statement", "txn", "activity", "account");
        for (SheetCandidate candidate : candidates) {
            String name = candidate.sheet.getSheetName().toLowerCase();
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    return candidate;
                }
            }
        }

        // Otherwise pick the sheet with the most rows
        candidates.sort((a, b) -> Integer.compare(b.sheet.getLastRowNum(), a.sheet.getLastRowNum()));
        return candidates.get(0);
    }

    private TransactionCandidate mapRowToCandidate(
        RawStatementRow rawRow,
        Row row,
        HeaderMapping mapping,
        UUID documentId,
        DocumentAccountType accountType
    ) {
        Cell dateCell = mapping.dateCol != null ? row.getCell(mapping.dateCol) : null;
        Cell descCell = mapping.descriptionCol != null ? row.getCell(mapping.descriptionCol) : null;
        Cell merchantCell = mapping.merchantCol != null ? row.getCell(mapping.merchantCol) : null;
        Cell debitCell = mapping.debitCol != null ? row.getCell(mapping.debitCol) : null;
        Cell creditCell = mapping.creditCol != null ? row.getCell(mapping.creditCol) : null;
        Cell amountCell = mapping.amountCol != null ? row.getCell(mapping.amountCol) : null;
        Cell typeCell = mapping.typeCol != null ? row.getCell(mapping.typeCol) : null;
        Cell balanceCell = mapping.balanceCol != null ? row.getCell(mapping.balanceCol) : null;
        Cell refCell = mapping.referenceCol != null ? row.getCell(mapping.referenceCol) : null;

        LocalDate date = extractDateFromCell(dateCell);
        String rawDescStr = getCellStringValue(descCell);
        String rawMerchantStr = getCellStringValue(merchantCell);
        String rawTypeStr = getCellStringValue(typeCell);
        String rawRefStr = getCellStringValue(refCell);

        ParsedAmount debit = extractAmountFromCell(debitCell);
        ParsedAmount credit = extractAmountFromCell(creditCell);
        ParsedAmount amount = extractAmountFromCell(amountCell);
        ParsedAmount balance = extractAmountFromCell(balanceCell);

        if (date == null && isEmpty(rawDescStr) && amount == null && debit == null && credit == null) {
            return null;
        }

        BigDecimal finalAmount = null;
        String rawTypeIndicator = rawTypeStr;
        String currency = "INR";

        if (debit != null && debit.amount().compareTo(BigDecimal.ZERO) > 0 && (credit == null || credit.amount().compareTo(BigDecimal.ZERO) == 0)) {
            finalAmount = debit.amount();
            if (rawTypeIndicator == null) rawTypeIndicator = "DEBIT";
            currency = debit.currency();
        } else if (credit != null && credit.amount().compareTo(BigDecimal.ZERO) > 0 && (debit == null || debit.amount().compareTo(BigDecimal.ZERO) == 0)) {
            finalAmount = credit.amount();
            if (rawTypeIndicator == null) rawTypeIndicator = "CREDIT";
            currency = credit.currency();
        } else if (debit != null && credit != null) {
            if (debit.amount().compareTo(BigDecimal.ZERO) > 0) {
                finalAmount = debit.amount();
                if (rawTypeIndicator == null) rawTypeIndicator = "DEBIT";
                currency = debit.currency();
            } else if (credit.amount().compareTo(BigDecimal.ZERO) > 0) {
                finalAmount = credit.amount();
                if (rawTypeIndicator == null) rawTypeIndicator = "CREDIT";
                currency = credit.currency();
            }
        } else if (amount != null) {
            finalAmount = amount.amount();
            if (rawTypeIndicator == null) {
                rawTypeIndicator = amount.negative() ? "DR" : "CR";
            }
            currency = amount.currency();
        }

        StatementNormalizer.ClassificationResult classificationResult = statementNormalizer.classifyTransaction(
            accountType,
            rawDescStr,
            rawMerchantStr,
            rawTypeIndicator,
            debit,
            credit,
            amount
        );

        PaymentMethod paymentMethod = statementNormalizer.inferPaymentMethod(accountType, rawDescStr, rawMerchantStr);
        String reference = statementNormalizer.extractReference(rawRefStr, rawDescStr);
        BigDecimal balanceAfter = balance != null ? balance.amount() : null;

        return TransactionCandidate.builder()
            .documentId(documentId)
            .rowIndex(rawRow.rowIndex())
            .transactionDate(date)
            .rawMerchant(rawMerchantStr)
            .rawDescription(rawDescStr != null ? rawDescStr.trim() : "")
            .amount(finalAmount)
            .direction(classificationResult.direction())
            .classification(classificationResult.classification())
            .inferredPaymentMethod(paymentMethod)
            .externalReference(reference)
            .balanceAfter(balanceAfter)
            .accountType(accountType)
            .currency(currency)
            .rawTypeIndicator(rawTypeIndicator)
            .build();
    }

    private LocalDate extractDateFromCell(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            } catch (Exception ignored) {
            }
        }

        String str = getCellStringValue(cell);
        return statementNormalizer.parseDate(str);
    }

    private ParsedAmount extractAmountFromCell(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            double numericVal = cell.getNumericCellValue();
            boolean negative = numericVal < 0;
            BigDecimal bd = BigDecimal.valueOf(Math.abs(numericVal));
            return new ParsedAmount(bd, negative, "INR");
        }

        String str = getCellStringValue(cell);
        return statementNormalizer.parseAmount(str);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell).trim();
    }

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !getCellStringValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isSummaryOrFooterRow(Row row, HeaderMapping mapping) {
        String first = row.getCell(0) != null ? getCellStringValue(row.getCell(0)).toLowerCase() : "";
        String desc = mapping.descriptionCol != null && row.getCell(mapping.descriptionCol) != null
            ? getCellStringValue(row.getCell(mapping.descriptionCol)).toLowerCase()
            : "";
        String date = mapping.dateCol != null && row.getCell(mapping.dateCol) != null
            ? getCellStringValue(row.getCell(mapping.dateCol)).toLowerCase()
            : "";

        String combined = (first + " " + desc + " " + date).trim();

        if (combined.startsWith("total") || combined.contains("grand total")
            || combined.contains("opening balance") || combined.contains("closing balance")
            || combined.contains("brought forward") || combined.contains("carried forward")
            || combined.contains("*** end of statement") || combined.startsWith("page ")
            || combined.contains("statement summary")) {
            return true;
        }

        return false;
    }

    private HeaderMapping detectHeaderMapping(Row row) {
        Integer dateCol = null;
        Integer descriptionCol = null;
        Integer merchantCol = null;
        Integer debitCol = null;
        Integer creditCol = null;
        Integer amountCol = null;
        Integer balanceCol = null;
        Integer referenceCol = null;
        Integer typeCol = null;

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell == null) continue;
            String raw = getCellStringValue(cell);
            if (raw.isBlank()) continue;

            String norm = normalizeHeader(raw);

            if (matchesDate(norm) && dateCol == null) {
                dateCol = i;
            } else if (matchesDebit(norm) && debitCol == null) {
                debitCol = i;
            } else if (matchesCredit(norm) && creditCol == null) {
                creditCol = i;
            } else if (matchesAmount(norm) && amountCol == null) {
                amountCol = i;
            } else if (matchesBalance(norm) && balanceCol == null) {
                balanceCol = i;
            } else if (matchesReference(norm) && referenceCol == null) {
                referenceCol = i;
            } else if (matchesType(norm) && typeCol == null) {
                typeCol = i;
            } else if (matchesMerchant(norm) && merchantCol == null) {
                merchantCol = i;
            } else if (matchesDescription(norm) && descriptionCol == null) {
                descriptionCol = i;
            }
        }

        boolean hasDate = dateCol != null;
        boolean hasDesc = descriptionCol != null || merchantCol != null;
        boolean hasMonetary = amountCol != null || (debitCol != null && creditCol != null) || debitCol != null;

        if (hasDate && hasDesc && hasMonetary) {
            return new HeaderMapping(
                dateCol,
                descriptionCol,
                merchantCol,
                debitCol,
                creditCol,
                amountCol,
                balanceCol,
                referenceCol,
                typeCol
            );
        }

        return null;
    }

    private static String normalizeHeader(String header) {
        return header.trim().toLowerCase()
            .replaceAll("[^a-z0-9]", "");
    }

    private static boolean matchesDate(String h) {
        return h.equals("date") || h.equals("txndate") || h.equals("transactiondate")
            || h.equals("valuedate") || h.equals("postingdate") || h.equals("transdate")
            || h.equals("bookingdate");
    }

    private static boolean matchesDescription(String h) {
        return h.equals("description") || h.equals("narration") || h.equals("particulars")
            || h.equals("transactiondetails") || h.equals("remarks") || h.equals("details")
            || h.equals("desc") || h.equals("transactionparticulars");
    }

    private static boolean matchesMerchant(String h) {
        return h.equals("merchant") || h.equals("payee") || h.equals("party") || h.equals("beneficiary");
    }

    private static boolean matchesDebit(String h) {
        return h.equals("debit") || h.equals("withdrawal") || h.equals("withdrawn")
            || h.equals("dr") || h.equals("debitamount") || h.equals("spent")
            || h.equals("withdrawalamt") || h.equals("dramount") || h.equals("withdrawals")
            || h.equals("debited") || h.equals("withdrawaldr") || h.equals("debitdr")
            || h.startsWith("debitinr") || h.startsWith("debitamount")
            || h.startsWith("withdrawalinr") || h.startsWith("withdrawalamount");
    }

    private static boolean matchesCredit(String h) {
        return h.equals("credit") || h.equals("deposit") || h.equals("deposited")
            || h.equals("cr") || h.equals("creditamount") || h.equals("received")
            || h.equals("depositamt") || h.equals("cramount") || h.equals("deposits")
            || h.equals("credited") || h.equals("creditcr") || h.equals("depositcr")
            || h.startsWith("creditinr") || h.startsWith("creditamount")
            || h.startsWith("depositinr") || h.startsWith("depositamount");
    }

    private static boolean matchesAmount(String h) {
        return h.equals("amount") || h.equals("transactionamount") || h.equals("txnamount")
            || h.equals("amt") || h.equals("totalamount") || h.equals("amountinr");
    }

    private static boolean matchesBalance(String h) {
        return h.equals("balance") || h.equals("closingbalance") || h.equals("availablebalance")
            || h.equals("bal") || h.equals("runningbalance") || h.equals("closingbal")
            || h.equals("accountbalance");
    }

    private static boolean matchesReference(String h) {
        return h.equals("reference") || h.equals("referenceno") || h.equals("transactionid")
            || h.equals("txnid") || h.equals("transactionreference") || h.equals("utr")
            || h.equals("rrn") || h.equals("chequeno") || h.equals("chqno")
            || h.equals("refno") || h.equals("ref") || h.equals("docno")
            || h.equals("chqrefno") || h.equals("chequereferenceno");
    }

    private static boolean matchesType(String h) {
        return h.equals("type") || h.equals("txntype") || h.equals("crdr") || h.equals("drcr")
            || h.equals("indicator") || h.equals("transactiontype");
    }

    private static boolean isEmpty(String str) {
        return str == null || str.isBlank();
    }

    private record HeaderMapping(
        Integer dateCol,
        Integer descriptionCol,
        Integer merchantCol,
        Integer debitCol,
        Integer creditCol,
        Integer amountCol,
        Integer balanceCol,
        Integer referenceCol,
        Integer typeCol
    ) {}

    private record SheetCandidate(
        Sheet sheet,
        HeaderMapping mapping,
        int headerRowIndex
    ) {}
}
