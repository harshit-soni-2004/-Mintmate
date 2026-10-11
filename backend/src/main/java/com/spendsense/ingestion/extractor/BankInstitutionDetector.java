package com.spendsense.ingestion.extractor;

import com.spendsense.document.DocumentAccountType;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust bank and account metadata extractor for statement documents.
 * Extracts canonical institution names, last 4 digits of account numbers,
 * and account types using keyword patterns and Reserve Bank of India IFSC code heuristics.
 */
public final class BankInstitutionDetector {

    private BankInstitutionDetector() {}

    private record BankDefinition(String canonicalName, Pattern pattern) {}

    private static final List<BankDefinition> BANK_DEFINITIONS = List.of(
            // State Bank of India
            new BankDefinition("State Bank of India",
                    Pattern.compile("\\b(STATE BANK OF INDIA|STATE BANK|\\bSBI\\b|\\bSBIN[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Kotak Mahindra Bank
            new BankDefinition("Kotak Mahindra Bank",
                    Pattern.compile("\\b(KOTAK MAHINDRA BANK|KOTAK BANK|\\bKOTAK\\b|\\bKKBK[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // HDFC Bank
            new BankDefinition("HDFC Bank",
                    Pattern.compile("\\b(HDFC BANK|HDFC LTD|\\bHDFC\\b|\\bHDFC[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // ICICI Bank
            new BankDefinition("ICICI Bank",
                    Pattern.compile("\\b(ICICI BANK|\\bICICI\\b|\\bICIC[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Axis Bank
            new BankDefinition("Axis Bank",
                    Pattern.compile("\\b(AXIS BANK|UTI BANK|\\bAXIS\\b|\\bUTIB[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Bank of Baroda
            new BankDefinition("Bank of Baroda",
                    Pattern.compile("\\b(BANK OF BARODA|\\bBOB\\b|\\bBARB[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Punjab National Bank
            new BankDefinition("Punjab National Bank",
                    Pattern.compile("\\b(PUNJAB NATIONAL BANK|\\bPNB\\b|\\bPUNB[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Canara Bank
            new BankDefinition("Canara Bank",
                    Pattern.compile("\\b(CANARA BANK|\\bCNRB[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Union Bank of India
            new BankDefinition("Union Bank of India",
                    Pattern.compile("\\b(UNION BANK OF INDIA|UNION BANK|\\bUBIN[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // IndusInd Bank
            new BankDefinition("IndusInd Bank",
                    Pattern.compile("\\b(INDUSIND BANK|\\bINDUSIND\\b|\\bINDB[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Yes Bank
            new BankDefinition("Yes Bank",
                    Pattern.compile("\\b(YES BANK|\\bYESB[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // Federal Bank
            new BankDefinition("Federal Bank",
                    Pattern.compile("\\b(FEDERAL BANK|\\bFDRL[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE)),

            // IDFC FIRST Bank
            new BankDefinition("IDFC FIRST Bank",
                    Pattern.compile("\\b(IDFC FIRST BANK|IDFC BANK|\\bIDFB[0-9A-Z]{7}\\b)", Pattern.CASE_INSENSITIVE))
    );

    private static final Pattern ACCOUNT_NUMBER_LABEL_PATTERN = Pattern.compile(
            "(?i)\\b(?:Account\\s*(?:Number|No\\.?|#)|A/c\\s*(?:Number|No\\.?|#))\\s*[:.\\-]?\\s*([0-9Xx*]{8,24})\\b"
    );

    private static final Pattern MASKED_ACCOUNT_PATTERN = Pattern.compile(
            "[X*x]{4,}([0-9]{4})\\b"
    );

    private static final Pattern ACCOUNT_TYPE_PATTERN = Pattern.compile(
            "(?i)\\b(SAVINGS|CURRENT|CREDIT CARD|\\bSB\\b)\\b"
    );

    /**
     * Inspects a collection of header text lines to infer institution name,
     * account number last 4 digits, and account type.
     */
    public static StatementDocumentMetadata detectMetadata(List<String> headerLines) {
        if (headerLines == null || headerLines.isEmpty()) {
            return StatementDocumentMetadata.empty();
        }

        String detectedInstitution = null;
        String detectedLast4 = null;
        DocumentAccountType detectedAccountType = null;

        String combinedText = String.join("\n", headerLines);

        // 1. Detect Institution Name
        for (BankDefinition def : BANK_DEFINITIONS) {
            if (def.pattern().matcher(combinedText).find()) {
                detectedInstitution = def.canonicalName();
                break;
            }
        }

        // 2. Detect Account Number Last 4
        for (String line : headerLines) {
            Matcher m = ACCOUNT_NUMBER_LABEL_PATTERN.matcher(line);
            if (m.find()) {
                String rawAcc = m.group(1).trim();
                Matcher masked = MASKED_ACCOUNT_PATTERN.matcher(rawAcc);
                if (masked.find()) {
                    detectedLast4 = masked.group(1);
                    break;
                } else if (rawAcc.length() >= 4) {
                    detectedLast4 = rawAcc.substring(rawAcc.length() - 4);
                    break;
                }
            }
        }

        // 3. Detect Account Type if present
        Matcher typeMatcher = ACCOUNT_TYPE_PATTERN.matcher(combinedText);
        if (typeMatcher.find()) {
            String typeStr = typeMatcher.group(1).toUpperCase();
            if (typeStr.contains("CREDIT")) {
                detectedAccountType = DocumentAccountType.CREDIT_CARD;
            } else if (typeStr.contains("CURRENT")) {
                detectedAccountType = DocumentAccountType.CURRENT;
            } else if (typeStr.contains("SAVINGS") || typeStr.equals("SB")) {
                detectedAccountType = DocumentAccountType.SAVINGS;
            }
        }

        return new StatementDocumentMetadata(
                detectedInstitution,
                detectedAccountType,
                detectedLast4,
                null,
                null
        );
    }
}
