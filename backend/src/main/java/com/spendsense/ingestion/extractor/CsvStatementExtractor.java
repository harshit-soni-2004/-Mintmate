package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.StatementNormalizer;
import com.spendsense.ingestion.StatementNormalizer.ParsedAmount;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Robust CSV statement extractor with dynamic header detection, synonym normalization,
 * metadata skipping, footer detection, and canonical candidate transformation.
 */
@Component
public class CsvStatementExtractor implements DocumentExtractor {

    private final StatementNormalizer statementNormalizer;
    private final CandidateValidator candidateValidator;

    public CsvStatementExtractor(StatementNormalizer statementNormalizer, CandidateValidator candidateValidator) {
        this.statementNormalizer = statementNormalizer;
        this.candidateValidator = candidateValidator;
    }

    @Override
    public boolean supports(DocumentFileType fileType) {
        return fileType == DocumentFileType.CSV;
    }

    @Override
    public StatementDocumentMetadata extractMetadata(InputStream stream) {
        if (stream == null) {
            return StatementDocumentMetadata.empty();
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            List<String> lines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null && lines.size() < 60) {
                lines.add(line);
            }
            return BankInstitutionDetector.detectMetadata(lines);
        } catch (Exception e) {
            return StatementDocumentMetadata.empty();
        }
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

        List<CSVRecord> records;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            CSVParser parser = CSVFormat.DEFAULT.builder()
                .setIgnoreSurroundingSpaces(true)
                .setAllowMissingColumnNames(true)
                .build()
                .parse(reader);
            records = parser.getRecords();
        } catch (Exception e) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.PARSING_ERROR,
                "Failed to parse CSV stream: " + e.getMessage(),
                e
            );
        }

        if (records.isEmpty()) {
            throw new DocumentExtractionException(DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT, "CSV file contains no records");
        }

        // 1. Detect header row
        HeaderMapping mapping = null;
        int headerRowIndex = -1;

        for (int i = 0; i < Math.min(records.size(), 50); i++) {
            CSVRecord record = records.get(i);
            HeaderMapping candidateMapping = detectHeaderMapping(record);
            if (candidateMapping != null) {
                mapping = candidateMapping;
                headerRowIndex = i;
                break;
            }
        }

        if (mapping == null) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.NO_HEADER_FOUND,
                "Could not identify a valid transaction table header in the CSV"
            );
        }

        // 2. Extract transaction candidate rows after header
        List<TransactionCandidate> candidates = new ArrayList<>();

        for (int i = headerRowIndex + 1; i < records.size(); i++) {
            CSVRecord record = records.get(i);

            if (isRecordEmpty(record)) {
                continue;
            }

            if (isSummaryOrFooterRow(record, mapping)) {
                // Summary row reached (e.g. Total, Closing Balance, Page footer)
                continue;
            }

            Map<String, String> cellValues = new LinkedHashMap<>();
            for (int col = 0; col < record.size(); col++) {
                cellValues.put("col_" + col, record.get(col));
            }
            RawStatementRow rawRow = new RawStatementRow(i + 1, cellValues, record.toString());

            TransactionCandidate candidate = mapRowToCandidate(rawRow, record, mapping, documentId, accountType);
            if (candidate != null) {
                TransactionCandidate validated = candidate.withValidationResult(candidateValidator.validate(candidate));
                candidates.add(validated);
            }
        }

        return candidates;
    }

    private TransactionCandidate mapRowToCandidate(
        RawStatementRow rawRow,
        CSVRecord record,
        HeaderMapping mapping,
        UUID documentId,
        DocumentAccountType accountType
    ) {
        String rawDateStr = getCell(record, mapping.dateCol);
        String rawDescStr = getCell(record, mapping.descriptionCol);
        String rawMerchantStr = getCell(record, mapping.merchantCol);
        String rawDebitStr = getCell(record, mapping.debitCol);
        String rawCreditStr = getCell(record, mapping.creditCol);
        String rawAmountStr = getCell(record, mapping.amountCol);
        String rawTypeStr = getCell(record, mapping.typeCol);
        String rawBalanceStr = getCell(record, mapping.balanceCol);
        String rawRefStr = getCell(record, mapping.referenceCol);

        // If completely empty of date and description and amounts, ignore
        if (isEmpty(rawDateStr) && isEmpty(rawDescStr) && isEmpty(rawAmountStr) && isEmpty(rawDebitStr) && isEmpty(rawCreditStr)) {
            return null;
        }

        LocalDate date = statementNormalizer.parseDate(rawDateStr);
        ParsedAmount debit = statementNormalizer.parseAmount(rawDebitStr);
        ParsedAmount credit = statementNormalizer.parseAmount(rawCreditStr);
        ParsedAmount amount = statementNormalizer.parseAmount(rawAmountStr);
        ParsedAmount balance = statementNormalizer.parseAmount(rawBalanceStr);

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

    private static String getCell(CSVRecord record, Integer col) {
        if (col == null || col < 0 || col >= record.size()) {
            return null;
        }
        String val = record.get(col);
        return val != null ? val.trim() : null;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.isBlank();
    }

    private static boolean isRecordEmpty(CSVRecord record) {
        for (String cell : record) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSummaryOrFooterRow(CSVRecord record, HeaderMapping mapping) {
        String firstCell = record.size() > 0 && record.get(0) != null ? record.get(0).trim().toLowerCase() : "";
        String descCell = mapping.descriptionCol != null && mapping.descriptionCol < record.size() && record.get(mapping.descriptionCol) != null
            ? record.get(mapping.descriptionCol).trim().toLowerCase()
            : "";
        String dateCell = mapping.dateCol != null && mapping.dateCol < record.size() && record.get(mapping.dateCol) != null
            ? record.get(mapping.dateCol).trim().toLowerCase()
            : "";

        String combined = (firstCell + " " + descCell + " " + dateCell).trim();

        if (combined.startsWith("total") || combined.contains("grand total")
            || combined.contains("opening balance") || combined.contains("closing balance")
            || combined.contains("brought forward") || combined.contains("carried forward")
            || combined.contains("*** end of statement") || combined.startsWith("page ")
            || combined.contains("statement summary")) {
            return true;
        }

        return false;
    }

    private HeaderMapping detectHeaderMapping(CSVRecord record) {
        Integer dateCol = null;
        Integer descriptionCol = null;
        Integer merchantCol = null;
        Integer debitCol = null;
        Integer creditCol = null;
        Integer amountCol = null;
        Integer balanceCol = null;
        Integer referenceCol = null;
        Integer typeCol = null;

        for (int i = 0; i < record.size(); i++) {
            String rawHeader = record.get(i);
            if (rawHeader == null || rawHeader.isBlank()) {
                continue;
            }
            String norm = normalizeHeader(rawHeader);

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

        // Header must have Date, and at least (Description or Merchant), and (Amount or (Debit and Credit))
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
}
