package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.ingestion.CandidateValidator;
import com.spendsense.ingestion.StatementNormalizer;
import com.spendsense.ingestion.StatementNormalizer.ClassificationResult;
import com.spendsense.ingestion.StatementNormalizer.ParsedAmount;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.PaymentMethod;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Text-based PDF statement extractor using Apache PDFBox 3.x.
 * Supports multi-page statements, coordinate-aware column mapping, repeated headers,
 * wrapped descriptions, summary row filtering, and structured exception handling for
 * encrypted, scanned, corrupted, or empty PDFs.
 */
@Component
public class PdfStatementExtractor implements DocumentExtractor {

    private final StatementNormalizer statementNormalizer;
    private final CandidateValidator candidateValidator;

    private static final Pattern DATE_START_PATTERN = Pattern.compile(
        "^\\s*(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}|\\d{1,2}[/\\-.][a-zA-Z]{3}[/\\-.]\\d{2,4}|\\d{1,2}\\s+[a-zA-Z]{3}\\s+\\d{2,4})\\b"
    );

    private static final Pattern TRANSACTION_LINE_START_PATTERN = Pattern.compile(
        "^\\s*(?:#?\\s*\\d{1,6}\\s+)?(\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}|\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}|\\d{1,2}[/\\-.][a-zA-Z]{3}[/\\-.]\\d{2,4}|\\d{1,2}\\s+[a-zA-Z]{3}\\s+\\d{2,4})\\b"
    );

    public PdfStatementExtractor(StatementNormalizer statementNormalizer, CandidateValidator candidateValidator) {
        this.statementNormalizer = statementNormalizer;
        this.candidateValidator = candidateValidator;
    }

    @Override
    public boolean supports(DocumentFileType fileType) {
        return fileType == DocumentFileType.PDF;
    }

    @Override
    public StatementDocumentMetadata extractMetadata(InputStream stream) {
        if (stream == null) {
            return StatementDocumentMetadata.empty();
        }
        try {
            byte[] bytes = stream.readAllBytes();
            if (bytes.length == 0) {
                return StatementDocumentMetadata.empty();
            }
            try (PDDocument document = Loader.loadPDF(bytes)) {
                if (document.isEncrypted() || document.getNumberOfPages() == 0) {
                    return StatementDocumentMetadata.empty();
                }
                StatementPdfTextStripper stripper = new StatementPdfTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(2, document.getNumberOfPages()));
                stripper.getText(document);
                List<String> headerLines = stripper.getExtractedLines().stream()
                        .map(TextLine::lineText)
                        .limit(60)
                        .toList();
                return BankInstitutionDetector.detectMetadata(headerLines);
            }
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
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT,
                "PDF document stream is null"
            );
        }

        byte[] bytes;
        try {
            bytes = stream.readAllBytes();
        } catch (IOException e) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.PARSING_ERROR,
                "Failed to read PDF document stream: " + e.getMessage(),
                e
            );
        }

        if (bytes.length == 0) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT,
                "PDF document stream is empty (0 bytes)"
            );
        }

        PDDocument document;
        try {
            document = Loader.loadPDF(bytes);
        } catch (InvalidPasswordException e) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.PASSWORD_PROTECTED_DOCUMENT,
                "PDF document is encrypted/password-protected",
                e
            );
        } catch (IOException e) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.CORRUPTED_FILE,
                "Failed to parse PDF document (corrupted or invalid format): " + e.getMessage(),
                e
            );
        }

        try {
            if (document.isEncrypted()) {
                throw new DocumentExtractionException(
                    DocumentExtractionException.ErrorCode.PASSWORD_PROTECTED_DOCUMENT,
                    "PDF document is encrypted/password-protected"
                );
            }

            if (document.getNumberOfPages() == 0) {
                throw new DocumentExtractionException(
                    DocumentExtractionException.ErrorCode.EMPTY_DOCUMENT,
                    "PDF document contains no pages"
                );
            }

            // Extract structured lines with word coordinates
            StatementPdfTextStripper stripper = new StatementPdfTextStripper();
            try {
                stripper.getText(document);
            } catch (IOException e) {
                throw new DocumentExtractionException(
                    DocumentExtractionException.ErrorCode.PARSING_ERROR,
                    "Failed to extract text from PDF: " + e.getMessage(),
                    e
                );
            }

            List<TextLine> lines = stripper.getExtractedLines();

            // Detect image-only / scanned PDF
            long totalCharCount = lines.stream()
                .mapToLong(l -> l.lineText().replaceAll("\\s+", "").length())
                .sum();
            if (totalCharCount < 10) {
                throw new DocumentExtractionException(
                    DocumentExtractionException.ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED,
                    "PDF appears to be scanned or image-only without extractable text. OCR is not supported."
                );
            }

            // Detect table header line
            HeaderInfo headerInfo = null;
            int headerLineIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                HeaderInfo candidate = detectHeader(lines.get(i));
                if (candidate != null) {
                    headerInfo = candidate;
                    headerLineIndex = i;
                    break;
                }
            }

            if (headerInfo != null) {
                try {
                    List<TransactionCandidate> headerCandidates = extractWithHeader(lines, headerLineIndex, headerInfo, documentId, accountType);
                    if (!headerCandidates.isEmpty()) {
                        return headerCandidates;
                    }
                } catch (DocumentExtractionException ignored) {
                    // Header-driven extraction produced no rows; fall back to coordinate layout inference
                }
            }

            return extractWithoutHeader(lines, documentId, accountType);
        } finally {
            try {
                document.close();
            } catch (IOException ignored) {
            }
        }
    }

    private List<TransactionCandidate> extractWithHeader(
        List<TextLine> lines,
        int headerLineIndex,
        HeaderInfo headerInfo,
        UUID documentId,
        DocumentAccountType accountType
    ) throws DocumentExtractionException {
        List<TransactionCandidate> candidates = new ArrayList<>();
        int rowIndex = 1;
        boolean canContinueNarration = false;

        for (int i = headerLineIndex + 1; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            String lineText = line.lineText().trim();

            if (lineText.isBlank()) {
                continue;
            }

            // Check repeated header on subsequent pages
            if (detectHeader(line) != null) {
                canContinueNarration = false;
                continue;
            }

            // Check footer / summary rows
            if (isSummaryOrFooterRow(lineText) || isHeaderLikeLine(lineText)) {
                canContinueNarration = false;
                if (lineText.toLowerCase(Locale.ROOT).contains("end of statement")) {
                    break;
                }
                continue;
            }

            // Check if line starts with a date
            Matcher dateMatcher = TRANSACTION_LINE_START_PATTERN.matcher(lineText);
            if (dateMatcher.find()) {
                // New transaction line
                ExtractedRowValues values = parseLineWithHeader(line, headerInfo);
                if (values != null && (values.debitStr != null || values.creditStr != null || values.amountStr != null)) {
                    Map<String, String> cellValues = new LinkedHashMap<>();
                    cellValues.put("date", values.dateStr);
                    cellValues.put("description", values.descStr);
                    cellValues.put("debit", values.debitStr);
                    cellValues.put("credit", values.creditStr);
                    cellValues.put("amount", values.amountStr);
                    cellValues.put("type", values.typeStr);
                    cellValues.put("balance", values.balanceStr);
                    cellValues.put("reference", values.refStr);

                    RawStatementRow rawRow = new RawStatementRow(rowIndex++, cellValues, lineText);
                    TransactionCandidate candidate = mapRowToCandidate(rawRow, values, documentId, accountType);
                    if (candidate != null) {
                        TransactionCandidate validated = candidate.withValidationResult(candidateValidator.validate(candidate));
                        candidates.add(validated);
                        canContinueNarration = true;
                    }
                }
            } else if (!candidates.isEmpty() && canContinueNarration) {
                // Possible wrapped narration / reference continuation
                if (isNarrationContinuation(line, headerInfo)) {
                    TransactionCandidate prev = candidates.get(candidates.size() - 1);
                    float firstColAfterDesc = headerInfo.refX != null ? headerInfo.refX : (headerInfo.debitX != null ? headerInfo.debitX : (headerInfo.amountX != null ? headerInfo.amountX : 350.0f));
                    float descEnd = firstColAfterDesc - 15.0f;

                    List<String> contDescWords = new ArrayList<>();
                    List<String> contRefWords = new ArrayList<>();
                    for (Word w : line.words()) {
                        if (w.x() < descEnd) {
                            contDescWords.add(w.text().trim());
                        } else {
                            contRefWords.add(w.text().trim());
                        }
                    }

                    String updatedDesc = prev.rawDescription();
                    if (!contDescWords.isEmpty()) {
                        String addDesc = String.join(" ", contDescWords);
                        updatedDesc = (updatedDesc + " " + addDesc).trim();
                    }

                    String updatedRef = prev.externalReference();
                    if (!contRefWords.isEmpty()) {
                        String addRef = String.join(" ", contRefWords);
                        if (updatedRef == null || updatedRef.isBlank()) {
                            updatedRef = addRef;
                        } else if (updatedRef.endsWith("-")) {
                            updatedRef = updatedRef + addRef;
                        } else {
                            updatedRef = updatedRef + " " + addRef;
                        }
                    }
                    updatedRef = statementNormalizer.extractReference(updatedRef, updatedDesc);

                    TransactionCandidate updatedCandidate = TransactionCandidate.builder()
                        .documentId(prev.documentId())
                        .rowIndex(prev.rowIndex())
                        .transactionDate(prev.transactionDate())
                        .rawMerchant(prev.rawMerchant())
                        .rawDescription(updatedDesc)
                        .amount(prev.amount())
                        .direction(prev.direction())
                        .classification(prev.classification())
                        .inferredPaymentMethod(statementNormalizer.inferPaymentMethod(accountType, updatedDesc, prev.rawMerchant()))
                        .externalReference(updatedRef)
                        .balanceAfter(prev.balanceAfter())
                        .accountType(prev.accountType())
                        .currency(prev.currency())
                        .rawTypeIndicator(prev.rawTypeIndicator())
                        .validationResult(prev.validationResult())
                        .build();

                    candidates.set(candidates.size() - 1, updatedCandidate.withValidationResult(candidateValidator.validate(updatedCandidate)));
                }
            }
        }

        if (candidates.isEmpty()) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.NO_HEADER_FOUND,
                "No transaction rows could be extracted from PDF document"
            );
        }

        return candidates;
    }

    private TransactionCandidate mapRowToCandidate(
        RawStatementRow rawRow,
        ExtractedRowValues values,
        UUID documentId,
        DocumentAccountType accountType
    ) {
        LocalDate date = statementNormalizer.parseDate(values.dateStr);
        ParsedAmount debit = statementNormalizer.parseAmount(values.debitStr);
        ParsedAmount credit = statementNormalizer.parseAmount(values.creditStr);
        ParsedAmount amount = statementNormalizer.parseAmount(values.amountStr);
        ParsedAmount balance = statementNormalizer.parseAmount(values.balanceStr);

        BigDecimal finalAmount = null;
        String rawTypeIndicator = values.typeStr;
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

        ClassificationResult classificationResult = statementNormalizer.classifyTransaction(
            accountType,
            values.descStr,
            null,
            rawTypeIndicator,
            debit,
            credit,
            amount
        );

        PaymentMethod paymentMethod = statementNormalizer.inferPaymentMethod(accountType, values.descStr, null);
        String reference = statementNormalizer.extractReference(values.refStr, values.descStr);
        BigDecimal balanceAfter = balance != null ? balance.amount() : null;

        return TransactionCandidate.builder()
            .documentId(documentId)
            .rowIndex(rawRow.rowIndex())
            .transactionDate(date)
            .rawDescription(values.descStr != null ? values.descStr.trim() : "")
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

    private ExtractedRowValues parseLineWithHeader(TextLine line, HeaderInfo header) {
        List<Word> words = line.words();
        if (words.isEmpty()) {
            return null;
        }

        Matcher dateMatcher = TRANSACTION_LINE_START_PATTERN.matcher(line.lineText().trim());
        if (!dateMatcher.find()) {
            return null;
        }
        String dateStr = dateMatcher.group(1);

        String descStr = null;
        String refStr = null;
        String debitStr = null;
        String creditStr = null;
        String amountStr = null;
        String typeStr = null;
        String balanceStr = null;

        float descStart = header.descX != null
            ? (header.dateX != null ? (header.dateX + header.descX) / 2.0f : header.descX - 10.0f)
            : 0f;

        if (header.hasSeparateDebitCredit()) {
            float firstColAfterDesc = header.refX != null ? header.refX : header.debitX;
            float descEnd = firstColAfterDesc - 15.0f;
            float refEnd = header.refEndX != null ? header.refEndX : (header.refX != null ? header.refX + 30.0f : descEnd);
            float debitThreshold = header.refX != null ? (refEnd + header.debitX) / 2.0f : descEnd;
            float debitEnd = header.debitEndX != null ? header.debitEndX : header.debitX;
            float creditThreshold = (debitEnd + header.creditX) / 2.0f;
            float creditEnd = header.creditEndX != null ? header.creditEndX : header.creditX;
            float balanceThreshold = header.balanceX != null ? (creditEnd + header.balanceX) / 2.0f : Float.MAX_VALUE;

            List<String> descTokens = new ArrayList<>();
            for (Word w : words) {
                float x = w.x();
                String t = w.text().trim();

                if (x < descStart) {
                    continue;
                }

                if (x < descEnd) {
                    if (descTokens.isEmpty() && t.matches("^\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}$")) {
                        continue;
                    }
                    descTokens.add(t);
                } else if (header.refX != null && x < debitThreshold) {
                    if (!t.equals("-")) refStr = (refStr == null ? "" : refStr + " ") + t;
                } else if (x < creditThreshold) {
                    if (!t.equals("-")) debitStr = (debitStr == null ? "" : debitStr + " ") + t;
                } else if (x < balanceThreshold) {
                    if (!t.equals("-")) creditStr = (creditStr == null ? "" : creditStr + " ") + t;
                } else {
                    if (!t.equals("-")) balanceStr = (balanceStr == null ? "" : balanceStr + " ") + t;
                }
            }
            descStr = String.join(" ", descTokens);
        } else {
            // Single amount column or amount + type
            float firstColAfterDesc = header.refX != null ? header.refX : (header.amountX != null ? header.amountX : 300.0f);
            float descEnd = firstColAfterDesc - 15.0f;
            float amountThreshold = (header.amountX != null && header.typeX != null)
                ? (header.amountX + header.typeX) / 2.0f
                : (header.balanceX != null ? (header.amountX + header.balanceX) / 2.0f : Float.MAX_VALUE);
            float typeThreshold = (header.typeX != null && header.balanceX != null)
                ? (header.typeX + header.balanceX) / 2.0f
                : Float.MAX_VALUE;
            Float amountEnd = header.amountEndX != null ? header.amountEndX : header.amountX;
            float balanceThreshold = header.balanceX != null
                ? (amountEnd != null ? (amountEnd + header.balanceX) / 2.0f : header.balanceX - 10.0f)
                : Float.MAX_VALUE;

            List<String> descTokens = new ArrayList<>();
            for (Word w : words) {
                float x = w.x();
                String t = w.text().trim();

                if (x < descStart) {
                    continue;
                }

                if (x < descEnd) {
                    if (descTokens.isEmpty() && t.matches("^\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}$")) {
                        continue;
                    }
                    descTokens.add(t);
                } else if (header.refX != null && Math.abs(x - header.refX) < 30.0f && isAlphanumericRef(t)) {
                    refStr = t;
                } else if (x < amountThreshold) {
                    amountStr = (amountStr == null ? "" : amountStr + " ") + t;
                } else if (header.typeX != null && x < typeThreshold) {
                    typeStr = (typeStr == null ? "" : typeStr + " ") + t;
                } else {
                    balanceStr = (balanceStr == null ? "" : balanceStr + " ") + t;
                }
            }
            descStr = String.join(" ", descTokens);
        }

        return new ExtractedRowValues(dateStr, descStr, refStr, debitStr, creditStr, amountStr, typeStr, balanceStr);
    }

    private boolean isNarrationContinuation(TextLine line, HeaderInfo header) {
        String text = line.lineText().trim();
        if (text.isBlank() || isSummaryOrFooterRow(text) || isHeaderLikeLine(text)) {
            return false;
        }

        // Must not start with date
        if (TRANSACTION_LINE_START_PATTERN.matcher(text).find()) {
            return false;
        }

        // Words should fall within the description column boundaries
        float cutoff = header.debitX != null ? header.debitX : (header.amountX != null ? header.amountX : 350.0f);
        boolean anyWordPastCutoff = line.words().stream().anyMatch(w -> w.x() >= cutoff - 15.0f);
        return !anyWordPastCutoff;
    }

    private static boolean isAlphanumericRef(String s) {
        return s != null && s.matches("^[A-Za-z0-9/\\-_]{6,20}$");
    }

    private static boolean isSummaryOrFooterRow(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("total") || lower.contains("grand total")
            || lower.contains("opening balance") || lower.contains("closing balance")
            || lower.contains("brought forward") || lower.contains("carried forward")
            || lower.contains("end of statement") || lower.startsWith("page ")
            || lower.contains("statement summary") || lower.contains("generated on")
            || lower.contains("please do not share") || lower.contains("computer generated")
            || lower.contains("power of attorney") || lower.contains("account summary")
            || lower.contains("system generated") || lower.contains("does not require signature");
    }

    private static boolean isHeaderLikeLine(String text) {
        String trimmed = text.trim();
        if (trimmed.equalsIgnoreCase("balance") || trimmed.equalsIgnoreCase("bal")) {
            return true;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("page no.") || lower.contains("page no.") || lower.matches("^\\d*\\s*page\\s+no\\.?.*");
    }

    private static boolean isAmountOrDashOrRef(String text) {
        String t = text.trim();
        return t.equals("-")
            || t.matches("^[\\d,]+\\.\\d{2}$")
            || isAlphanumericRef(t);
    }

    private static boolean isWithinDescriptionBounds(TextLine line, float descEndX) {
        return line.words().stream().allMatch(w -> w.x() < descEndX + 10.0f);
    }

    private List<TransactionCandidate> extractWithoutHeader(
        List<TextLine> lines,
        UUID documentId,
        DocumentAccountType accountType
    ) throws DocumentExtractionException {
        // 1. Identify candidate transaction anchor lines
        List<Integer> anchorIndices = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            TextLine line = lines.get(i);
            String text = line.lineText().trim();
            if (isSummaryOrFooterRow(text) || isHeaderLikeLine(text)) {
                continue;
            }
            if (DATE_START_PATTERN.matcher(text).find()) {
                boolean hasRightToken = line.words().stream()
                    .anyMatch(w -> w.x() > 200.0f && isAmountOrDashOrRef(w.text().trim()));
                if (hasRightToken) {
                    anchorIndices.add(i);
                }
            }
        }

        if (anchorIndices.isEmpty()) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.NO_HEADER_FOUND,
                "No transaction table header or recognizable transaction rows found in PDF document"
            );
        }

        // 2. Infer column positions from repeated transaction anchors
        InferredLayout layout = inferLayoutFromAnchors(lines, anchorIndices);

        // 3. Extract transaction candidates across all anchors
        List<TransactionCandidate> candidates = new ArrayList<>();
        int rowIndex = 1;

        for (int a = 0; a < anchorIndices.size(); a++) {
            int anchorIdx = anchorIndices.get(a);
            TextLine anchorLine = lines.get(anchorIdx);
            int nextAnchorIdx = (a + 1 < anchorIndices.size()) ? anchorIndices.get(a + 1) : lines.size();

            // Check prefix narration line (immediately before anchorLine within <= 15 points)
            String prefixDesc = "";
            if (anchorIdx > 0) {
                TextLine prevLine = lines.get(anchorIdx - 1);
                float dy = anchorLine.y() - prevLine.y();
                if (prevLine.page() == anchorLine.page() && dy > 0 && dy <= 15.0f
                        && !isSummaryOrFooterRow(prevLine.lineText())
                        && !isHeaderLikeLine(prevLine.lineText())
                        && isWithinDescriptionBounds(prevLine, layout.descEndX())) {
                    prefixDesc = prevLine.lineText().trim();
                }
            }

            // Extract anchor line values
            ExtractedRowValues values = parseLineWithLayout(anchorLine, layout);
            String debitStr = values.debitStr();
            String creditStr = values.creditStr();
            String amountStr = values.amountStr();
            String balanceStr = values.balanceStr();
            String refStr = values.refStr();

            // Collect narration continuation lines and fallback amounts
            StringBuilder continuationDesc = new StringBuilder();
            for (int c = anchorIdx + 1; c < nextAnchorIdx; c++) {
                // If c is the prefix line of the next anchor, stop continuation collection
                if (nextAnchorIdx < lines.size() && c == nextAnchorIdx - 1) {
                    TextLine nextAnchor = lines.get(nextAnchorIdx);
                    TextLine candPrefix = lines.get(c);
                    float dy = nextAnchor.y() - candPrefix.y();
                    if (candPrefix.page() == nextAnchor.page() && dy > 0 && dy <= 15.0f
                            && !isSummaryOrFooterRow(candPrefix.lineText())
                            && !isHeaderLikeLine(candPrefix.lineText())
                            && isWithinDescriptionBounds(candPrefix, layout.descEndX())) {
                        break;
                    }
                }

                TextLine contLine = lines.get(c);
                String cText = contLine.lineText().trim();
                if (cText.isBlank() || isHeaderLikeLine(cText)) {
                    continue;
                }
                if (isSummaryOrFooterRow(cText)) {
                    break;
                }

                ExtractedRowValues contValues = parseLineWithLayout(contLine, layout);
                if (debitStr == null && contValues.debitStr() != null) debitStr = contValues.debitStr();
                if (creditStr == null && contValues.creditStr() != null) creditStr = contValues.creditStr();
                if (amountStr == null && contValues.amountStr() != null) amountStr = contValues.amountStr();
                if (balanceStr == null && contValues.balanceStr() != null) balanceStr = contValues.balanceStr();
                if (refStr == null && contValues.refStr() != null) refStr = contValues.refStr();

                if (contValues.descStr() != null && !contValues.descStr().isBlank()) {
                    if (continuationDesc.length() > 0) continuationDesc.append(" ");
                    continuationDesc.append(contValues.descStr());
                }
            }

            StringBuilder fullDesc = new StringBuilder();
            if (!prefixDesc.isBlank()) fullDesc.append(prefixDesc);
            if (values.descStr() != null && !values.descStr().isBlank()) {
                if (fullDesc.length() > 0) fullDesc.append(" ");
                fullDesc.append(values.descStr());
            }
            if (continuationDesc.length() > 0) {
                if (fullDesc.length() > 0) fullDesc.append(" ");
                fullDesc.append(continuationDesc);
            }

            ExtractedRowValues finalValues = new ExtractedRowValues(
                values.dateStr(),
                fullDesc.toString(),
                refStr,
                debitStr,
                creditStr,
                amountStr,
                values.typeStr(),
                balanceStr
            );

            Map<String, String> cellValues = new LinkedHashMap<>();
            cellValues.put("date", finalValues.dateStr());
            cellValues.put("description", finalValues.descStr());
            cellValues.put("debit", finalValues.debitStr());
            cellValues.put("credit", finalValues.creditStr());
            cellValues.put("amount", finalValues.amountStr());
            cellValues.put("type", finalValues.typeStr());
            cellValues.put("balance", finalValues.balanceStr());
            cellValues.put("reference", finalValues.refStr());

            RawStatementRow rawRow = new RawStatementRow(rowIndex++, cellValues, anchorLine.lineText().trim());
            TransactionCandidate candidate = mapRowToCandidate(rawRow, finalValues, documentId, accountType);
            if (candidate != null) {
                TransactionCandidate validated = candidate.withValidationResult(candidateValidator.validate(candidate));
                candidates.add(validated);
            }
        }

        if (candidates.isEmpty()) {
            throw new DocumentExtractionException(
                DocumentExtractionException.ErrorCode.NO_HEADER_FOUND,
                "No transaction rows could be extracted from PDF document"
            );
        }

        return candidates;
    }

    private InferredLayout inferLayoutFromAnchors(List<TextLine> lines, List<Integer> anchorIndices) {
        List<Float> rightXPositions = new ArrayList<>();
        int secondDateCount = 0;
        float date2SumX = 0f;

        for (int idx : anchorIndices) {
            TextLine line = lines.get(idx);
            List<Word> words = line.words();
            if (words.size() > 1 && DATE_START_PATTERN.matcher(words.get(1).text()).find()) {
                secondDateCount++;
                date2SumX += words.get(1).x();
            }
            for (Word w : words) {
                String t = w.text().trim();
                if (w.x() > 200.0f && isAmountOrDashOrRef(t)) {
                    rightXPositions.add(w.x());
                }
            }
        }

        Collections.sort(rightXPositions);
        List<List<Float>> rawClusters = new ArrayList<>();
        for (float x : rightXPositions) {
            if (rawClusters.isEmpty() || Math.abs(x - rawClusters.get(rawClusters.size() - 1).get(0)) > 25.0f) {
                List<Float> newCluster = new ArrayList<>();
                newCluster.add(x);
                rawClusters.add(newCluster);
            } else {
                rawClusters.get(rawClusters.size() - 1).add(x);
            }
        }

        int minCount = Math.max(2, (int) (anchorIndices.size() * 0.15));
        List<Float> clusterX = new ArrayList<>();
        for (List<Float> c : rawClusters) {
            if (c.size() >= minCount) {
                float avg = (float) c.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
                clusterX.add(avg);
            }
        }

        Float date1X = lines.get(anchorIndices.get(0)).words().get(0).x();
        Float date2X = (secondDateCount >= anchorIndices.size() * 0.4) ? (date2SumX / secondDateCount) : null;

        Float refX = null;
        Float debitX = null;
        Float creditX = null;
        Float amountX = null;
        Float balanceX = null;

        if (clusterX.size() >= 4) {
            refX = clusterX.get(0);
            debitX = clusterX.get(1);
            creditX = clusterX.get(2);
            balanceX = clusterX.get(3);
        } else if (clusterX.size() == 3) {
            debitX = clusterX.get(0);
            creditX = clusterX.get(1);
            balanceX = clusterX.get(2);
        } else if (clusterX.size() == 2) {
            amountX = clusterX.get(0);
            balanceX = clusterX.get(1);
        } else if (clusterX.size() == 1) {
            amountX = clusterX.get(0);
        }

        float descEndX = (refX != null ? refX : (debitX != null ? debitX : (amountX != null ? amountX : 280.0f))) - 15.0f;

        return new InferredLayout(date1X, date2X, descEndX, refX, debitX, creditX, amountX, balanceX);
    }

    private ExtractedRowValues parseLineWithLayout(TextLine line, InferredLayout layout) {
        List<Word> words = line.words();
        if (words.isEmpty()) {
            return new ExtractedRowValues(null, null, null, null, null, null, null, null);
        }

        String dateStr = null;
        int startWordIdx = 0;

        if (DATE_START_PATTERN.matcher(words.get(0).text()).find()) {
            dateStr = words.get(0).text();
            startWordIdx = 1;
            if (words.size() > 1 && layout.date2X() != null && DATE_START_PATTERN.matcher(words.get(1).text()).find()) {
                startWordIdx = 2;
            }
        }

        StringBuilder descBuf = new StringBuilder();
        String refStr = null;
        String debitStr = null;
        String creditStr = null;
        String amountStr = null;
        String balanceStr = null;

        if (layout.hasSeparateDebitCredit()) {
            float debitThreshold = layout.refX() != null ? (layout.refX() + layout.debitX()) / 2.0f : layout.descEndX();
            float creditThreshold = (layout.debitX() + layout.creditX()) / 2.0f;
            float balanceThreshold = layout.balanceX() != null ? (layout.creditX() + layout.balanceX()) / 2.0f : Float.MAX_VALUE;

            for (int i = startWordIdx; i < words.size(); i++) {
                Word w = words.get(i);
                float x = w.x();
                String t = w.text().trim();

                if (x < layout.descEndX()) {
                    if (descBuf.length() > 0) descBuf.append(" ");
                    descBuf.append(t);
                } else if (layout.refX() != null && x < debitThreshold) {
                    if (!t.equals("-")) refStr = (refStr == null ? "" : refStr + " ") + t;
                } else if (x < creditThreshold) {
                    if (!t.equals("-")) debitStr = (debitStr == null ? "" : debitStr + " ") + t;
                } else if (x < balanceThreshold) {
                    if (!t.equals("-")) creditStr = (creditStr == null ? "" : creditStr + " ") + t;
                } else {
                    if (!t.equals("-")) balanceStr = (balanceStr == null ? "" : balanceStr + " ") + t;
                }
            }
        } else {
            float amountThreshold = layout.amountX() != null ? layout.descEndX() : 300.0f;
            float balanceThreshold = layout.balanceX() != null ? (layout.amountX() + layout.balanceX()) / 2.0f : Float.MAX_VALUE;

            for (int i = startWordIdx; i < words.size(); i++) {
                Word w = words.get(i);
                float x = w.x();
                String t = w.text().trim();

                if (x < layout.descEndX()) {
                    if (descBuf.length() > 0) descBuf.append(" ");
                    descBuf.append(t);
                } else if (layout.refX() != null && layout.amountX() != null && x < (layout.refX() + layout.amountX()) / 2.0f) {
                    if (!t.equals("-")) refStr = (refStr == null ? "" : refStr + " ") + t;
                } else if (x < balanceThreshold) {
                    if (!t.equals("-")) amountStr = (amountStr == null ? "" : amountStr + " ") + t;
                } else {
                    if (!t.equals("-")) balanceStr = (balanceStr == null ? "" : balanceStr + " ") + t;
                }
            }
        }

        return new ExtractedRowValues(
            dateStr,
            descBuf.length() > 0 ? descBuf.toString() : null,
            refStr,
            debitStr,
            creditStr,
            amountStr,
            null,
            balanceStr
        );
    }

    private HeaderInfo detectHeader(TextLine line) {
        List<Word> words = line.words();
        if (words.size() < 3) {
            return null;
        }

        Float dateX = null;
        Float descX = null;
        Float descEndX = null;
        Float refX = null;
        Float refEndX = null;
        Float debitX = null;
        Float debitEndX = null;
        Float creditX = null;
        Float creditEndX = null;
        Float amountX = null;
        Float amountEndX = null;
        Float typeX = null;
        Float balanceX = null;

        for (Word w : words) {
            String norm = w.text().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");

            if (matchesDateHeader(norm) && dateX == null) {
                dateX = w.x();
            } else if (matchesDescHeader(norm) && descX == null) {
                descX = w.x();
                descEndX = w.x() + w.width();
            } else if (matchesRefHeader(norm) || (refX != null && (norm.equals("no") || norm.equals("num")))) {
                if (refX == null) refX = w.x();
                refEndX = w.x() + w.width();
            } else if (matchesDebitHeader(norm)) {
                if (debitX == null) debitX = w.x();
                debitEndX = w.x() + w.width();
            } else if (matchesCreditHeader(norm)) {
                if (creditX == null) creditX = w.x();
                creditEndX = w.x() + w.width();
            } else if (matchesAmountHeader(norm)) {
                if (amountX == null) amountX = w.x();
                amountEndX = w.x() + w.width();
            } else if (matchesTypeHeader(norm) && typeX == null) {
                typeX = w.x();
            } else if (matchesBalanceHeader(norm) && balanceX == null) {
                balanceX = w.x();
            }
        }

        boolean hasDate = dateX != null;
        boolean hasDesc = descX != null;
        boolean hasMonetary = amountX != null || (debitX != null && creditX != null) || debitX != null;

        if (hasDate && hasDesc && hasMonetary) {
            if (descEndX == null) {
                descEndX = (debitX != null ? debitX : amountX) - 20.0f;
            }
            return new HeaderInfo(dateX, descX, descEndX, refX, refEndX, debitX, debitEndX, creditX, creditEndX, amountX, amountEndX, typeX, balanceX);
        }

        return null;
    }

    private static boolean matchesDateHeader(String h) {
        return h.equals("date") || h.equals("txndate") || h.equals("transactiondate")
            || h.equals("valuedate") || h.equals("postingdate");
    }

    private static boolean matchesDescHeader(String h) {
        return h.equals("narration") || h.equals("description") || h.equals("particulars")
            || h.equals("details") || h.equals("transactiondetails") || h.equals("merchant");
    }

    private static boolean matchesRefHeader(String h) {
        return h.equals("ref") || h.equals("refno") || h.equals("chqref") || h.equals("reference")
            || h.equals("chqrefno") || h.equals("utr") || h.equals("chequeno");
    }

    private static boolean matchesDebitHeader(String h) {
        return h.equals("withdrawal") || h.equals("withdrawals") || h.equals("debit")
            || h.equals("dr") || h.equals("withdrawn") || h.equals("withdrawaldr")
            || h.equals("debitdr") || h.startsWith("debit") || h.startsWith("withdrawal");
    }

    private static boolean matchesCreditHeader(String h) {
        return h.equals("deposit") || h.equals("deposits") || h.equals("credit")
            || h.equals("cr") || h.equals("deposited") || h.equals("depositcr")
            || h.equals("creditcr") || h.startsWith("credit") || h.startsWith("deposit");
    }

    private static boolean matchesAmountHeader(String h) {
        return h.equals("amount") || h.equals("amt") || h.equals("txnamount") || h.equals("amountinr");
    }

    private static boolean matchesTypeHeader(String h) {
        return h.equals("type") || h.equals("txntype") || h.equals("crdr") || h.equals("drcr");
    }

    private static boolean matchesBalanceHeader(String h) {
        return h.equals("balance") || h.equals("bal") || h.equals("closingbalance");
    }

    private record HeaderInfo(
        Float dateX,
        Float descX,
        Float descEndX,
        Float refX,
        Float refEndX,
        Float debitX,
        Float debitEndX,
        Float creditX,
        Float creditEndX,
        Float amountX,
        Float amountEndX,
        Float typeX,
        Float balanceX
    ) {
        public boolean hasSeparateDebitCredit() {
            return debitX != null && creditX != null;
        }
    }

    private record InferredLayout(
        Float date1X,
        Float date2X,
        Float descEndX,
        Float refX,
        Float debitX,
        Float creditX,
        Float amountX,
        Float balanceX
    ) {
        public boolean hasSeparateDebitCredit() {
            return debitX != null && creditX != null;
        }
    }

    private record ExtractedRowValues(
        String dateStr,
        String descStr,
        String refStr,
        String debitStr,
        String creditStr,
        String amountStr,
        String typeStr,
        String balanceStr
    ) {}

    public record Word(String text, float x, float y, float width, int page) {}

    public record TextLine(List<Word> words, float y, int page) {
        public String lineText() {
            return words.stream().map(Word::text).collect(Collectors.joining(" "));
        }
    }

    /**
     * Custom PDFTextStripper that collects individual words with precise page and X/Y coordinates.
     */
    static class StatementPdfTextStripper extends PDFTextStripper {

        private final List<TextLine> lines = new ArrayList<>();
        private final List<Word> currentLineWords = new ArrayList<>();
        private Float currentY = null;

        public StatementPdfTextStripper() {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            if (textPositions == null || textPositions.isEmpty()) {
                return;
            }

            StringBuilder wordBuf = new StringBuilder();
            Float wordStartX = null;
            Float wordEndX = null;
            Float wordY = null;
            int page = getCurrentPageNo();

            for (TextPosition tp : textPositions) {
                String unicode = tp.getUnicode();
                if (unicode == null || unicode.isBlank()) {
                    if (wordBuf.length() > 0 && wordStartX != null) {
                        addWord(new Word(wordBuf.toString(), wordStartX, wordY, wordEndX - wordStartX, page));
                        wordBuf.setLength(0);
                        wordStartX = null;
                    }
                } else {
                    if (wordBuf.length() == 0) {
                        wordStartX = tp.getXDirAdj();
                        wordY = tp.getYDirAdj();
                    }
                    wordBuf.append(unicode);
                    wordEndX = tp.getXDirAdj() + tp.getWidthDirAdj();
                }
            }

            if (wordBuf.length() > 0 && wordStartX != null) {
                addWord(new Word(wordBuf.toString(), wordStartX, wordY, wordEndX - wordStartX, page));
            }
        }

        private void addWord(Word word) {
            if (currentY == null || Math.abs(word.y() - currentY) > 4.0f) {
                flushLine();
                currentY = word.y();
            }
            currentLineWords.add(word);
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            flushLine();
        }

        @Override
        protected void endPage(PDPage page) throws IOException {
            flushLine();
            super.endPage(page);
        }

        private void flushLine() {
            if (!currentLineWords.isEmpty()) {
                currentLineWords.sort(Comparator.comparingDouble(Word::x));
                lines.add(new TextLine(new ArrayList<>(currentLineWords), currentY != null ? currentY : 0f, getCurrentPageNo()));
                currentLineWords.clear();
            }
            currentY = null;
        }

        public List<TextLine> getExtractedLines() {
            flushLine();
            return lines;
        }
    }
}
