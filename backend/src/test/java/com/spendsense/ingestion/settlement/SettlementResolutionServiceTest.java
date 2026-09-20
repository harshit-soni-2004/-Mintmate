package com.spendsense.ingestion.settlement;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.document.DocumentFileType;
import com.spendsense.document.FinancialDocument;
import com.spendsense.ingestion.CandidateValidationResult;
import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.ingestion.TransactionCandidate;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementResolutionServiceTest {

    @Mock
    private StatementSettlementRepository settlementRepository;

    private SettlementResolutionService settlementService;
    private User testUser;
    private FinancialDocument bankDoc;
    private FinancialDocument cardDoc;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementResolutionService(settlementRepository);
        testUser = new User("test@spendsense.com", "password123", "Test User", "Asia/Kolkata");
        bankDoc = new FinancialDocument(
                testUser, "bank.pdf", DocumentFileType.PDF, 1024, "key1", "hash1",
                "HDFC Bank", DocumentAccountType.SAVINGS, "1234", null, null);
        cardDoc = new FinancialDocument(
                testUser, "card.pdf", DocumentFileType.PDF, 2048, "key2", "hash2",
                "HDFC Bank", DocumentAccountType.CREDIT_CARD, "5678", null, null);
    }

    private TransactionCandidate createCandidate(
            UUID docId,
            int rowIndex,
            LocalDate date,
            BigDecimal amount,
            String merchant,
            SourceTransactionClassification classification,
            DocumentAccountType accountType
    ) {
        return new TransactionCandidate(
                docId,
                rowIndex,
                date,
                merchant,
                merchant,
                amount,
                null, // settlements and refunds preserve direction = null
                classification,
                PaymentMethod.BANK_TRANSFER,
                null,
                null,
                accountType,
                "INR",
                "PAYMENT",
                CandidateValidationResult.valid()
        );
    }

    @Test
    @DisplayName("Should record bank credit-card payment as UNRESOLVED when no card statement exists yet")
    void shouldRecordUnresolvedBankPayment() {
        TransactionCandidate bankPayment = createCandidate(
                bankDoc.getId(), 1, LocalDate.of(2026, 3, 10), new BigDecimal("20000.00"),
                "PAYMENT TO HDFC CREDIT CARD", SourceTransactionClassification.PAYMENT, DocumentAccountType.SAVINGS);

        when(settlementRepository.findByUserIdAndStatusAndAmount(
                testUser.getId(), SettlementStatus.UNRESOLVED, new BigDecimal("20000.00")))
                .thenReturn(List.of());

        when(settlementRepository.save(any(StatementSettlement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettlementResolutionService.SettlementResolutionOutcome outcome =
                settlementService.processCandidate(testUser, bankDoc, bankPayment);

        assertTrue(outcome.isSettlementOrRefund());
        assertFalse(outcome.isResolved());
        assertNotNull(outcome.currentSettlement());
        assertEquals(SettlementStatus.UNRESOLVED, outcome.currentSettlement().getStatus());
        assertEquals(new BigDecimal("20000.00"), outcome.currentSettlement().getAmount());
    }

    @Test
    @DisplayName("Should match and resolve settlement when opposing card payment arrives")
    void shouldMatchBankAndCardSettlement() {
        LocalDate bankDate = LocalDate.of(2026, 3, 10);
        LocalDate cardDate = LocalDate.of(2026, 3, 11); // 1 day lag
        BigDecimal amount = new BigDecimal("20000.00");

        StatementSettlement existingBankSettlement = new StatementSettlement(
                testUser, bankDoc, 1, bankDate, amount,
                "PAYMENT TO HDFC CREDIT CARD", "PAYMENT TO HDFC CREDIT CARD",
                SourceTransactionClassification.PAYMENT, DocumentAccountType.SAVINGS,
                null, SettlementStatus.UNRESOLVED);

        TransactionCandidate cardPaymentReceived = createCandidate(
                cardDoc.getId(), 5, cardDate, amount,
                "PAYMENT RECEIVED", SourceTransactionClassification.PAYMENT, DocumentAccountType.CREDIT_CARD);

        when(settlementRepository.findByUserIdAndStatusAndAmount(
                testUser.getId(), SettlementStatus.UNRESOLVED, amount))
                .thenReturn(List.of(existingBankSettlement));

        when(settlementRepository.save(any(StatementSettlement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettlementResolutionService.SettlementResolutionOutcome outcome =
                settlementService.processCandidate(testUser, cardDoc, cardPaymentReceived);

        assertTrue(outcome.isSettlementOrRefund());
        assertTrue(outcome.isResolved());
        assertEquals(SettlementStatus.RESOLVED, outcome.currentSettlement().getStatus());
        assertEquals(SettlementStatus.RESOLVED, existingBankSettlement.getStatus());
        assertEquals(existingBankSettlement.getId(), outcome.currentSettlement().getMatchedSettlementId());
        assertEquals(outcome.currentSettlement().getId(), existingBankSettlement.getMatchedSettlementId());
    }

    @Test
    @DisplayName("Should record refund as UNRESOLVED offset without converting to income")
    void shouldRecordRefundAsUnresolved() {
        TransactionCandidate refundCandidate = createCandidate(
                cardDoc.getId(), 2, LocalDate.of(2026, 3, 12), new BigDecimal("1500.00"),
                "AMAZON REFUND", SourceTransactionClassification.REFUND, DocumentAccountType.CREDIT_CARD);

        when(settlementRepository.save(any(StatementSettlement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettlementResolutionService.SettlementResolutionOutcome outcome =
                settlementService.processCandidate(testUser, cardDoc, refundCandidate);

        assertTrue(outcome.isSettlementOrRefund());
        assertFalse(outcome.isResolved());
        assertEquals(SourceTransactionClassification.REFUND, outcome.currentSettlement().getClassification());
        assertEquals(SettlementStatus.UNRESOLVED, outcome.currentSettlement().getStatus());
    }

    @Test
    @DisplayName("Should NOT match settlements if transaction dates are beyond allowed window")
    void shouldNotMatchSettlementsBeyondDateWindow() {
        LocalDate bankDate = LocalDate.of(2026, 3, 1);
        LocalDate cardDate = LocalDate.of(2026, 3, 20); // 19 days difference
        BigDecimal amount = new BigDecimal("5000.00");

        StatementSettlement existingBankSettlement = new StatementSettlement(
                testUser, bankDoc, 1, bankDate, amount,
                "PAYMENT TO HDFC CC", "PAYMENT TO HDFC CC",
                SourceTransactionClassification.PAYMENT, DocumentAccountType.SAVINGS,
                null, SettlementStatus.UNRESOLVED);

        TransactionCandidate cardPayment = createCandidate(
                cardDoc.getId(), 1, cardDate, amount,
                "PAYMENT RECEIVED", SourceTransactionClassification.PAYMENT, DocumentAccountType.CREDIT_CARD);

        when(settlementRepository.findByUserIdAndStatusAndAmount(
                testUser.getId(), SettlementStatus.UNRESOLVED, amount))
                .thenReturn(List.of(existingBankSettlement));

        when(settlementRepository.save(any(StatementSettlement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettlementResolutionService.SettlementResolutionOutcome outcome =
                settlementService.processCandidate(testUser, cardDoc, cardPayment);

        assertTrue(outcome.isSettlementOrRefund());
        assertFalse(outcome.isResolved()); // Not resolved due to excessive date gap
        assertEquals(SettlementStatus.UNRESOLVED, outcome.currentSettlement().getStatus());
    }
}
