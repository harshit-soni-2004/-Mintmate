package com.spendsense.ingestion;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class StatementNormalizerTest {

    private StatementNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new StatementNormalizer();
    }

    @Test
    void parseDate_supportsStandardIndianAndIsoFormats() {
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("25/08/2026"));
        assertEquals(LocalDate.of(2026, 8, 5), normalizer.parseDate("5/8/2026"));
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("25-08-2026"));
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("2026-08-25"));
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("2026/08/25"));
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("25-Aug-2026"));
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("25-AUG-2026"));
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("25-Aug-26"));
        assertEquals(LocalDate.of(2026, 8, 25), normalizer.parseDate("25.08.2026"));
    }

    @Test
    void parseDate_neverSwapsMonthAndDayAmbiguously() {
        // In Indian banking 03/04/2026 is 3rd April 2026, NOT March 4th
        LocalDate parsed = normalizer.parseDate("03/04/2026");
        assertNotNull(parsed);
        assertEquals(LocalDate.of(2026, 4, 3), parsed);
    }

    @Test
    void parseDate_returnsNullForInvalidOrEmpty() {
        assertNull(normalizer.parseDate(null));
        assertNull(normalizer.parseDate(""));
        assertNull(normalizer.parseDate("   "));
        assertNull(normalizer.parseDate("invalid-date"));
        assertNull(normalizer.parseDate("99/99/9999"));
    }

    @Test
    void parseAmount_cleansCurrencySymbolsAndDetectsCurrencyExplicitly() {
        var a1 = normalizer.parseAmount("₹1,234.50");
        assertNotNull(a1);
        assertEquals(new BigDecimal("1234.50"), a1.amount());
        assertFalse(a1.negative());
        assertEquals("INR", a1.currency());
        assertTrue(a1.currencyExplicitlyDetected());

        var a2 = normalizer.parseAmount("1,234.50");
        assertNotNull(a2);
        assertEquals(new BigDecimal("1234.50"), a2.amount());
        assertFalse(a2.negative());
        assertEquals("INR", a2.currency());
        assertFalse(a2.currencyExplicitlyDetected()); // Application default

        var a3 = normalizer.parseAmount("-1234.50");
        assertNotNull(a3);
        assertEquals(new BigDecimal("1234.50"), a3.amount());
        assertTrue(a3.negative());

        var a4 = normalizer.parseAmount("(1,234.50)");
        assertNotNull(a4);
        assertEquals(new BigDecimal("1234.50"), a4.amount());
        assertTrue(a4.negative());

        var a5 = normalizer.parseAmount("₹ 500");
        assertNotNull(a5);
        assertEquals(new BigDecimal("500"), a5.amount());
        assertTrue(a5.currencyExplicitlyDetected());

        var a6 = normalizer.parseAmount("$99.99");
        assertNotNull(a6);
        assertEquals(new BigDecimal("99.99"), a6.amount());
        assertEquals("USD", a6.currency());
        assertTrue(a6.currencyExplicitlyDetected());

        var a7 = normalizer.parseAmount("€45.00");
        assertNotNull(a7);
        assertEquals(new BigDecimal("45.00"), a7.amount());
        assertEquals("EUR", a7.currency());
        assertTrue(a7.currencyExplicitlyDetected());
    }

    @Test
    void parseAmount_returnsNullForInvalid() {
        assertNull(normalizer.parseAmount(null));
        assertNull(normalizer.parseAmount(""));
        assertNull(normalizer.parseAmount("N/A"));
        assertNull(normalizer.parseAmount("invalid"));
    }

    // ==========================================
    // SIX CORE SEMANTIC CLASSIFICATION TESTS
    // ==========================================

    @Test
    void classifyTransaction_creditCardPurchase_mapsToPurchaseAndExpense() {
        var amt = new StatementNormalizer.ParsedAmount(new BigDecimal("642.00"), false, "INR", true);
        var result = normalizer.classifyTransaction(
            DocumentAccountType.CREDIT_CARD,
            "SWIGGY ₹642 PURCHASE",
            "SWIGGY",
            "PURCHASE",
            null,
            null,
            amt
        );

        assertEquals(SourceTransactionClassification.PURCHASE, result.classification());
        assertEquals(TransactionType.EXPENSE, result.direction());
    }

    @Test
    void classifyTransaction_creditCardPayment_mapsToPaymentAndNullDirection() {
        // Must NOT automatically become INCOME!
        var amt = new StatementNormalizer.ParsedAmount(new BigDecimal("20000.00"), false, "INR", true);
        var result = normalizer.classifyTransaction(
            DocumentAccountType.CREDIT_CARD,
            "PAYMENT RECEIVED ₹20,000",
            null,
            "PAYMENT",
            null,
            null,
            amt
        );

        assertEquals(SourceTransactionClassification.PAYMENT, result.classification());
        assertNull(result.direction(), "Card payment must NOT be automatically classified as INCOME");
    }

    @Test
    void classifyTransaction_creditCardRefund_mapsToRefundAndNullDirection() {
        // Must NOT automatically become ordinary INCOME!
        var amt = new StatementNormalizer.ParsedAmount(new BigDecimal("2000.00"), false, "INR", true);
        var result = normalizer.classifyTransaction(
            DocumentAccountType.CREDIT_CARD,
            "AMAZON REFUND ₹2,000",
            "AMAZON",
            "REFUND",
            null,
            null,
            amt
        );

        assertEquals(SourceTransactionClassification.REFUND, result.classification());
        assertNull(result.direction(), "Card refund must NOT be automatically classified as ordinary INCOME");
    }

    @Test
    void classifyTransaction_bankSalaryCredit_mapsToDepositAndIncome() {
        var credit = new StatementNormalizer.ParsedAmount(new BigDecimal("80000.00"), false, "INR", true);
        var result = normalizer.classifyTransaction(
            DocumentAccountType.SAVINGS,
            "SALARY CREDIT ₹80,000",
            null,
            "CREDIT",
            null,
            credit,
            null
        );

        assertEquals(SourceTransactionClassification.DEPOSIT, result.classification());
        assertEquals(TransactionType.INCOME, result.direction());
    }

    @Test
    void classifyTransaction_bankCreditCardPayment_mapsToPaymentAndNullDirection() {
        // Must NOT be treated as ordinary EXPENSE!
        var debit = new StatementNormalizer.ParsedAmount(new BigDecimal("20000.00"), false, "INR", true);
        var result = normalizer.classifyTransaction(
            DocumentAccountType.SAVINGS,
            "PAYMENT TO HDFC CREDIT CARD ₹20,000",
            null,
            "DEBIT",
            debit,
            null,
            null
        );

        assertEquals(SourceTransactionClassification.PAYMENT, result.classification());
        assertNull(result.direction(), "Bank payment to credit card must NOT be automatically ordinary EXPENSE");
    }

    @Test
    void classifyTransaction_bankUpiExpense_mapsToPurchaseAndExpense() {
        var debit = new StatementNormalizer.ParsedAmount(new BigDecimal("642.00"), false, "INR", true);
        var result = normalizer.classifyTransaction(
            DocumentAccountType.SAVINGS,
            "UPI TO SWIGGY ₹642",
            "SWIGGY",
            "DEBIT",
            debit,
            null,
            null
        );

        assertEquals(SourceTransactionClassification.PURCHASE, result.classification());
        assertEquals(TransactionType.EXPENSE, result.direction());
    }

    @Test
    void classifyTransaction_feesAndInterest() {
        var debit = new StatementNormalizer.ParsedAmount(new BigDecimal("150.00"), false, "INR", false);
        var feeResult = normalizer.classifyTransaction(
            DocumentAccountType.SAVINGS,
            "SMS CHARGE Q2",
            null,
            "DEBIT",
            debit,
            null,
            null
        );
        assertEquals(SourceTransactionClassification.FEE, feeResult.classification());
        assertEquals(TransactionType.EXPENSE, feeResult.direction());

        var credit = new StatementNormalizer.ParsedAmount(new BigDecimal("450.00"), false, "INR", false);
        var intResult = normalizer.classifyTransaction(
            DocumentAccountType.SAVINGS,
            "SAVINGS INT.PD",
            null,
            "CREDIT",
            null,
            credit,
            null
        );
        assertEquals(SourceTransactionClassification.INTEREST, intResult.classification());
        assertEquals(TransactionType.INCOME, intResult.direction());
    }

    @Test
    void inferPaymentMethod_conservativeInference() {
        // Credit card account
        assertEquals(PaymentMethod.CREDIT_CARD,
            normalizer.inferPaymentMethod(DocumentAccountType.CREDIT_CARD, "Amazon Retail", "Amazon"));

        // UPI rails in savings
        assertEquals(PaymentMethod.UPI,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "UPI/423456789012/Swiggy/swiggy@icici", null));
        assertEquals(PaymentMethod.UPI,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "Payment to user@okhdfcbank", null));
        assertEquals(PaymentMethod.UPI,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "UPI_REF_987654321012", null));

        // Bank transfers
        assertEquals(PaymentMethod.BANK_TRANSFER,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "NEFT CR-N12345678-EMPLOYER SALARY", null));
        assertEquals(PaymentMethod.BANK_TRANSFER,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "IMPS/P2A/456789012345/RENT", null));
        assertEquals(PaymentMethod.BANK_TRANSFER,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "RTGS UTIBR520260825001", null));

        // Cash / ATM
        assertEquals(PaymentMethod.CASH,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "ATM WDL CASH 10000 KORAMANGALA", null));

        // Debit card POS
        assertEquals(PaymentMethod.DEBIT_CARD,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "POS 4532XXXXXXXX1234 RELIANCE SMART", null));

        // Fallback
        assertEquals(PaymentMethod.OTHER,
            normalizer.inferPaymentMethod(DocumentAccountType.SAVINGS, "DIVIDEND RECEIVED TCS", null));
    }

    @Test
    void extractReference_dedicatedOrNarration() {
        assertEquals("REF12345", normalizer.extractReference("REF12345", "UPI payment"));
        assertEquals("423456789012", normalizer.extractReference(null, "UPI/423456789012/Swiggy Bangalore"));
        assertNull(normalizer.extractReference(null, "Short description"));
    }
}
