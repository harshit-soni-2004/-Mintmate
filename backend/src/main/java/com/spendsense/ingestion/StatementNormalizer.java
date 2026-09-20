package com.spendsense.ingestion;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic statement normalizer handling:
 * - Multi-pattern date parsing with strict day-first conventions
 * - High-precision monetary parsing with currency detection and application default preservation
 * - Source-level financial event classification (PURCHASE, PAYMENT, REFUND, REVERSAL, FEE, INTEREST, TRANSFER, DEPOSIT, WITHDRAWAL)
 * - Safe deferred direction (EXPENSE/INCOME) assignment to prevent card settlements from becoming income
 * - Conservative payment method inference from rails and account type
 */
@Component
public class StatementNormalizer {

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
        createFormatter("dd/MM/yyyy"),
        createFormatter("d/M/yyyy"),
        createFormatter("dd-MM-yyyy"),
        createFormatter("d-M-yyyy"),
        createFormatter("yyyy-MM-dd"),
        createFormatter("yyyy/MM/dd"),
        createFormatter("dd-MMM-yyyy"),
        createFormatter("d-MMM-yyyy"),
        createFormatter("dd-MMM-yy"),
        createFormatter("d-MMM-yy"),
        createFormatter("dd MMM yyyy"),
        createFormatter("d MMM yyyy"),
        createFormatter("dd/MM/yy"),
        createFormatter("d/M/yy"),
        createFormatter("dd.MM.yyyy"),
        createFormatter("d.M.yyyy")
    );

    private static final Pattern PARENTHESES_AMOUNT = Pattern.compile("^\\s*\\((.*)\\)\\s*$");
    private static final Pattern CURRENCY_SYMBOL = Pattern.compile("[₹$€£]|Rs\\.?|INR|USD|EUR|GBP", Pattern.CASE_INSENSITIVE);

    // Payment method rails
    private static final Pattern UPI_VPA_PATTERN = Pattern.compile("[a-zA-Z0-9.\\-_]+@(okhdfcbank|oksbi|okicici|okaxis|paytm|ybl|upi|axl|ibl|apl|idfcbank|kotak|[a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPI_MARKER_PATTERN = Pattern.compile("\\b(UPI[/\\-_:]|UPI |UPI_REF|VPA\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_TRANSFER_PATTERN = Pattern.compile("\\b(NEFT|RTGS|IMPS|ACH[/\\-]|NACH|INFT|FUND TRANSFER|FT[/\\-]|TRANSFER TO|TRANSFER FROM|CHQ TRANSFER)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CASH_PATTERN = Pattern.compile("\\b(ATM WDL|ATM CASH|CASH WDL|CASH DEP|ATM[/\\-]CASH)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBIT_CARD_PATTERN = Pattern.compile("\\b(POS[/ ]|ECOM[/ ]|E-COM|DEBIT CARD|CARD PURCHASE|ATM/DEBIT|VISA|MASTERCARD|RUPAY)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UTR_RRN_PATTERN = Pattern.compile("\\b([0-9]{12})\\b");

    // Semantic classification patterns
    private static final Pattern CARD_PAYMENT_PATTERN = Pattern.compile("\\b(PAYMENT RECEIVED|AUTOPAY|BILLPAY|PYMT|CRED|AUTO DEBIT|PAYMENT|CR\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern REFUND_REVERSAL_PATTERN = Pattern.compile("\\b(REFUND|REVERSAL|REVERSE|CHARGEBACK|CASHBACK|RET|RETURN)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_CC_PAYMENT_PATTERN = Pattern.compile("\\b(CREDIT CARD|CC PAYMENT|HDFC CC|SBICARD|ICICI CARD|AXIS CARD|CRED|CARD PAYMENT|CC BILL)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL_TRANSFER_PATTERN = Pattern.compile("\\b(OWN ACC|INTERNAL TRF|SELF TRF|TRF TO SELF|TRF FROM SELF|TRANSFER TO ACC|TRANSFER FROM ACC)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern INTEREST_PATTERN = Pattern.compile("\\b(INTEREST CREDITED|INT\\.PD|SAVINGS INT|FD INTEREST|INTEREST PAID|INTEREST CHARGE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FEE_PATTERN = Pattern.compile("\\b(ANNUAL FEE|ANNUAL CHARGE|SMS CHARGE|DEBIT CARD AMC|MIN BAL CHARGE|PENALTY|LATE PAYMENT|FINANCE CHARGE|GST|IGST|CGST|SGST|CHARGES|FEE)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SALARY_PATTERN = Pattern.compile("\\b(SALARY|PAYROLL|STIPEND)\\b", Pattern.CASE_INSENSITIVE);

    private static DateTimeFormatter createFormatter(String pattern) {
        return new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern(pattern)
            .toFormatter(Locale.ENGLISH);
    }

    public record ParsedAmount(BigDecimal amount, boolean negative, String currency, boolean currencyExplicitlyDetected) {
        public ParsedAmount(BigDecimal amount, boolean negative, String currency) {
            this(amount, negative, currency, false);
        }
    }

    public record ClassificationResult(
        SourceTransactionClassification classification,
        TransactionType direction
    ) {}

    /**
     * Parses a date string deterministically across common Indian and ISO formats.
     * Rejects ambiguous interpretations rather than guessing month/day swap.
     */
    public LocalDate parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }

        String cleaned = rawDate.trim();

        // Check if string is an Excel serial date number (e.g., "45500" or "45500.0")
        if (cleaned.matches("^\\d{5}(\\.\\d+)?$")) {
            try {
                double serial = Double.parseDouble(cleaned);
                long wholeDays = (long) serial;
                // Excel 1900 date system has a leap year bug (day 60 = Feb 29, 1900)
                if (wholeDays > 60) {
                    wholeDays--;
                }
                return LocalDate.of(1899, 12, 31).plusDays(wholeDays);
            } catch (Exception ignored) {
                // fall through to formatters
            }
        }

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next pattern
            }
        }

        return null;
    }

    /**
     * Normalizes a monetary amount string into a strictly positive BigDecimal,
     * detecting negative formatting (minus sign or parentheses) and currency symbols.
     * Currency resolution:
     * - Detected explicitly if source contains symbols (₹, $, €, £) or ISO codes (USD, EUR, GBP, INR, RS).
     * - Otherwise defaults to application default "INR" without silent currency conversion.
     */
    public ParsedAmount parseAmount(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return null;
        }

        String cleaned = rawAmount.trim();

        // Detect currency
        String currency = "INR";
        boolean explicitCurrency = false;
        String upper = cleaned.toUpperCase(Locale.ROOT);
        if (cleaned.contains("$") || upper.contains("USD")) {
            currency = "USD";
            explicitCurrency = true;
        } else if (cleaned.contains("€") || upper.contains("EUR")) {
            currency = "EUR";
            explicitCurrency = true;
        } else if (cleaned.contains("£") || upper.contains("GBP")) {
            currency = "GBP";
            explicitCurrency = true;
        } else if (cleaned.contains("₹") || upper.contains("INR") || upper.contains("RS")) {
            currency = "INR";
            explicitCurrency = true;
        }

        // Check parentheses accounting format e.g. (1,234.50)
        boolean negative = false;
        Matcher parenMatcher = PARENTHESES_AMOUNT.matcher(cleaned);
        if (parenMatcher.matches()) {
            negative = true;
            cleaned = parenMatcher.group(1).trim();
        }

        // Strip currency symbols and letters
        cleaned = CURRENCY_SYMBOL.matcher(cleaned).replaceAll("").trim();

        // Check explicit minus sign
        if (cleaned.startsWith("-")) {
            negative = true;
            cleaned = cleaned.substring(1).trim();
        } else if (cleaned.endsWith("-")) {
            negative = true;
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        // Remove thousand separators and whitespace
        cleaned = cleaned.replace(",", "").replaceAll("\\s+", "");

        if (cleaned.isEmpty()) {
            return null;
        }

        try {
            BigDecimal value = new BigDecimal(cleaned);
            return new ParsedAmount(value.abs(), negative, currency, explicitCurrency);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Classifies the raw financial event and determines whether it maps to a provisional
     * TransactionType (EXPENSE/INCOME) or leaves direction null for Phase 7E settlement processing.
     * Prevents credit card payments, bill payments, internal transfers, and refunds from prematurely
     * becoming ordinary income or expense.
     */
    public ClassificationResult classifyTransaction(
        DocumentAccountType accountType,
        String narration,
        String rawMerchant,
        String rawType,
        ParsedAmount debitAmount,
        ParsedAmount creditAmount,
        ParsedAmount singleAmount
    ) {
        String combined = ((narration != null ? narration : "") + " "
            + (rawMerchant != null ? rawMerchant : "") + " "
            + (rawType != null ? rawType : "")).trim();
        String upperType = rawType != null ? rawType.trim().toUpperCase(Locale.ROOT) : "";

        // Determine if credit (money in) vs debit (money out)
        boolean isDebit = (debitAmount != null && debitAmount.amount().compareTo(BigDecimal.ZERO) > 0)
            || (upperType.startsWith("DR") || upperType.contains("DEBIT") || upperType.contains("WDL") || upperType.contains("WITHDRAW") || upperType.equals("PURCHASE"))
            || (singleAmount != null && singleAmount.negative());

        boolean isCredit = (creditAmount != null && creditAmount.amount().compareTo(BigDecimal.ZERO) > 0)
            || (upperType.startsWith("CR") || upperType.contains("CREDIT") || upperType.contains("DEP") || upperType.contains("DEPOSIT") || upperType.contains("PAYMENT") || upperType.contains("REFUND"));

        // If single amount column without explicit sign or column indicator:
        if (!isDebit && !isCredit && singleAmount != null && singleAmount.amount().compareTo(BigDecimal.ZERO) > 0) {
            if (SALARY_PATTERN.matcher(combined).find() || upperType.contains("CR") || combined.toUpperCase(Locale.ROOT).contains("CREDIT") || combined.toUpperCase(Locale.ROOT).contains("DEPOSIT")) {
                isCredit = true;
            } else {
                isDebit = true;
            }
        }

        // ==========================================
        // 1. CREDIT CARD ACCOUNTS
        // ==========================================
        if (accountType == DocumentAccountType.CREDIT_CARD) {
            // Check card payment received (settlement payment towards card)
            if (upperType.contains("PAYMENT") || CARD_PAYMENT_PATTERN.matcher(combined).find()) {
                // Must not become income!
                return new ClassificationResult(SourceTransactionClassification.PAYMENT, null);
            }

            // Check card refund / reversal
            if (upperType.contains("REFUND") || upperType.contains("REVERSAL") || REFUND_REVERSAL_PATTERN.matcher(combined).find()) {
                // Must not become ordinary income!
                return new ClassificationResult(SourceTransactionClassification.REFUND, null);
            }

            // Check interest / finance charge
            if (INTEREST_PATTERN.matcher(combined).find()) {
                return new ClassificationResult(SourceTransactionClassification.INTEREST, TransactionType.EXPENSE);
            }

            // Check fee / taxes
            if (FEE_PATTERN.matcher(combined).find()) {
                return new ClassificationResult(SourceTransactionClassification.FEE, TransactionType.EXPENSE);
            }

            // Default card charge is a purchase
            return new ClassificationResult(SourceTransactionClassification.PURCHASE, TransactionType.EXPENSE);
        }

        // ==========================================
        // 2. BANK ACCOUNTS (SAVINGS / CURRENT)
        // ==========================================
        if (isDebit) {
            // Check credit card bill payment from bank account
            if (BANK_CC_PAYMENT_PATTERN.matcher(combined).find()) {
                // Internal account transfer / settlement - NOT ordinary expense!
                return new ClassificationResult(SourceTransactionClassification.PAYMENT, null);
            }

            // Check internal account transfer
            if (INTERNAL_TRANSFER_PATTERN.matcher(combined).find()) {
                return new ClassificationResult(SourceTransactionClassification.TRANSFER, null);
            }

            // Check fee
            if (FEE_PATTERN.matcher(combined).find()) {
                return new ClassificationResult(SourceTransactionClassification.FEE, TransactionType.EXPENSE);
            }

            // Check cash withdrawal
            if (CASH_PATTERN.matcher(combined).find()) {
                return new ClassificationResult(SourceTransactionClassification.WITHDRAWAL, TransactionType.EXPENSE);
            }

            // Check purchase or general withdrawal
            if (UPI_MARKER_PATTERN.matcher(combined).find() || UPI_VPA_PATTERN.matcher(combined).find()
                || DEBIT_CARD_PATTERN.matcher(combined).find() || upperType.equals("PURCHASE")) {
                return new ClassificationResult(SourceTransactionClassification.PURCHASE, TransactionType.EXPENSE);
            }

            return new ClassificationResult(SourceTransactionClassification.WITHDRAWAL, TransactionType.EXPENSE);
        }

        if (isCredit) {
            // Check refund / reversal
            if (REFUND_REVERSAL_PATTERN.matcher(combined).find()) {
                // NOT ordinary earned income!
                return new ClassificationResult(SourceTransactionClassification.REFUND, null);
            }

            // Check internal account transfer
            if (INTERNAL_TRANSFER_PATTERN.matcher(combined).find()) {
                return new ClassificationResult(SourceTransactionClassification.TRANSFER, null);
            }

            // Check interest credited
            if (INTEREST_PATTERN.matcher(combined).find()) {
                return new ClassificationResult(SourceTransactionClassification.INTEREST, TransactionType.INCOME);
            }

            // Check salary / general deposit
            return new ClassificationResult(SourceTransactionClassification.DEPOSIT, TransactionType.INCOME);
        }

        return new ClassificationResult(SourceTransactionClassification.UNKNOWN, null);
    }

    /**
     * Conservatively infers payment method based on account type, payment rail markers, and narration.
     */
    public PaymentMethod inferPaymentMethod(
        DocumentAccountType accountType,
        String narration,
        String rawMerchant
    ) {
        if (accountType == DocumentAccountType.CREDIT_CARD) {
            return PaymentMethod.CREDIT_CARD;
        }

        String combined = ((narration != null ? narration : "") + " " + (rawMerchant != null ? rawMerchant : "")).trim();
        if (combined.isEmpty()) {
            return PaymentMethod.OTHER;
        }

        if (UPI_MARKER_PATTERN.matcher(combined).find() || UPI_VPA_PATTERN.matcher(combined).find()) {
            return PaymentMethod.UPI;
        }

        if (BANK_TRANSFER_PATTERN.matcher(combined).find()) {
            return PaymentMethod.BANK_TRANSFER;
        }

        if (CASH_PATTERN.matcher(combined).find()) {
            return PaymentMethod.CASH;
        }

        if (DEBIT_CARD_PATTERN.matcher(combined).find()) {
            return PaymentMethod.DEBIT_CARD;
        }

        return PaymentMethod.OTHER;
    }

    /**
     * Extracts external reference if present in dedicated field or parses UTR/RRN from narration.
     */
    public String extractReference(String dedicatedReference, String narration) {
        if (dedicatedReference != null && !dedicatedReference.isBlank()) {
            return dedicatedReference.trim();
        }

        if (narration != null && !narration.isBlank()) {
            Matcher matcher = UTR_RRN_PATTERN.matcher(narration);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        return null;
    }
}
