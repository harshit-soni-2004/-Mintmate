package com.spendsense.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.spendsense.common.exception.ApiException;
import com.spendsense.debt.dto.CreateDebtRequest;
import com.spendsense.debt.dto.DebtCandidateTransactionDto;
import com.spendsense.debt.dto.DebtDto;
import com.spendsense.debt.dto.DebtSummaryDto;
import com.spendsense.debt.dto.SettleDebtRequest;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.IncomeTier;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class DebtServiceTest {

    @Mock
    private DebtRepository debtRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private DebtMatchDismissalRepository dismissalRepository;

    @Mock
    private com.spendsense.account.FinancialAccountRepository accountRepository;

    private DebtService debtService;
    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        debtService = new DebtService(debtRepository, transactionRepository, userRepository, currentUser, dismissalRepository, accountRepository);

        testUser = new User("user@example.com", "hash", "User", "Asia/Kolkata");
        userId = testUser.getId();

        SpendSenseUserPrincipal principal = new SpendSenseUserPrincipal(testUser);
        when(currentUser.require()).thenReturn(principal);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
    }

    @Test
    @DisplayName("Create debt logs receivable (OWED_TO_ME) with ACTIVE status")
    void testCreateReceivableDebt() {
        CreateDebtRequest request = new CreateDebtRequest(
                "Rohan Sharma",
                DebtType.OWED_TO_ME,
                new BigDecimal("2500.00"),
                LocalDate.now().plusDays(10),
                "Lunch split"
        );

        when(debtRepository.save(any(Debt.class))).thenAnswer(inv -> {
            Debt d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DebtDto result = debtService.createDebt(request);

        assertThat(result.counterpartyName()).isEqualTo("Rohan Sharma");
        assertThat(result.debtType()).isEqualTo(DebtType.OWED_TO_ME);
        assertThat(result.amount()).isEqualByComparingTo("2500.00");
        assertThat(result.status()).isEqualTo(DebtStatus.ACTIVE);
        verify(debtRepository).save(any(Debt.class));
    }

    @Test
    @DisplayName("Debt summary accurately aggregates receivables, payables, net position and reminders")
    void testGetDebtSummary() {
        when(debtRepository.sumActiveAmountByUserIdAndDebtType(userId, DebtType.OWED_TO_ME))
                .thenReturn(new BigDecimal("5000.00"));
        when(debtRepository.sumActiveAmountByUserIdAndDebtType(userId, DebtType.I_OWE))
                .thenReturn(new BigDecimal("1500.00"));

        Debt overdue = new Debt(testUser, "Amit", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), LocalDate.now().minusDays(3), "Overdue loan");
        overdue.setId(UUID.randomUUID());

        Debt upcoming = new Debt(testUser, "Neha", DebtType.I_OWE, new BigDecimal("500.00"), LocalDate.now().plusDays(5), "Concert ticket");
        upcoming.setId(UUID.randomUUID());

        when(debtRepository.findOverdueDebts(eq(userId), any(LocalDate.class))).thenReturn(List.of(overdue));
        when(debtRepository.findUpcomingDebts(eq(userId), any(LocalDate.class))).thenReturn(List.of(upcoming));
        when(debtRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, DebtStatus.ACTIVE))
                .thenReturn(List.of(overdue, upcoming));

        DebtSummaryDto summary = debtService.getDebtSummary();

        assertThat(summary.totalReceivable()).isEqualByComparingTo("5000.00");
        assertThat(summary.totalPayable()).isEqualByComparingTo("1500.00");
        assertThat(summary.netDebtPosition()).isEqualByComparingTo("3500.00");
        assertThat(summary.activeCount()).isEqualTo(2);
        assertThat(summary.overdueCount()).isEqualTo(1);
        assertThat(summary.overdueDebts()).hasSize(1);
        assertThat(summary.upcomingDebts()).hasSize(1);
    }

    @Test
    @DisplayName("Settling OWED_TO_ME debt with transaction reclassifies transaction to avoid income double counting")
    void testSettleOwedToMeWithTransactionReconciliation() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rahul", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), LocalDate.now().plusDays(2), "Loan repayment");
        debt.setId(debtId);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        Transaction incomingTx = new Transaction(testUser, new BigDecimal("2000.00"), "Repayment",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "Notes");
        UUID txId = incomingTx.getId();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(incomingTx));
        when(debtRepository.save(any(Debt.class))).thenAnswer(inv -> inv.getArgument(0));

        SettleDebtRequest settleRequest = new SettleDebtRequest(txId);
        DebtDto settled = debtService.settleDebt(debtId, settleRequest);

        assertThat(settled.status()).isEqualTo(DebtStatus.SETTLED);
        assertThat(settled.linkedTransactionId()).isEqualTo(txId);

        // Crucial: Incoming credit transaction must be reclassified so it doesn't count as salary/income!
        assertThat(incomingTx.getIncomeTier()).isEqualTo(IncomeTier.TIER_3_REFUND);
        assertThat(incomingTx.getCategory()).isEqualTo(TransactionCategory.OTHER);
        assertThat(incomingTx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(incomingTx.getTransferDirection()).isEqualTo(TransferDirection.IN);
        verify(transactionRepository).save(incomingTx);
    }

    @Test
    @DisplayName("Settling I_OWE debt with transaction reclassifies transaction to avoid expense double counting")
    void testSettleIOweWithTransactionReconciliation() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Vikram", DebtType.I_OWE, new BigDecimal("3500.00"), LocalDate.now().plusDays(1), "Hotel share");
        debt.setId(debtId);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        Transaction outgoingTx = new Transaction(testUser, new BigDecimal("3500.00"), "Hotel share",
                TransactionCategory.TRAVEL, TransactionType.EXPENSE, LocalDate.now(), "Notes");
        UUID txId = outgoingTx.getId();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(outgoingTx));
        when(debtRepository.save(any(Debt.class))).thenAnswer(inv -> inv.getArgument(0));

        SettleDebtRequest settleRequest = new SettleDebtRequest(txId);
        DebtDto settled = debtService.settleDebt(debtId, settleRequest);

        assertThat(settled.status()).isEqualTo(DebtStatus.SETTLED);
        assertThat(settled.linkedTransactionId()).isEqualTo(txId);

        // Crucial: Outgoing debit transaction must be protected from polluting category expense budget!
        assertThat(outgoingTx.getCategory()).isEqualTo(TransactionCategory.OTHER);
        assertThat(outgoingTx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(outgoingTx.getTransferDirection()).isEqualTo(TransferDirection.OUT);
        verify(transactionRepository).save(outgoingTx);
    }

    @Test
    @DisplayName("Settling debt with transaction belonging to another user is rejected with 403 Forbidden")
    void testSettleDebtRejectsForeignUserTransaction() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rahul", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), null, null);
        debt.setId(debtId);
        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        User anotherUser = new User("other@example.com", "hash", "Other", "Asia/Kolkata");
        Transaction foreignTx = new Transaction(anotherUser, new BigDecimal("2000.00"), "Repayment",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "Notes");
        UUID txId = foreignTx.getId();

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(foreignTx));

        assertThatThrownBy(() -> debtService.settleDebt(debtId, new SettleDebtRequest(txId)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancel debt transitions status to CANCELLED")
    void testCancelDebt() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rahul", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.save(any(Debt.class))).thenAnswer(inv -> inv.getArgument(0));

        DebtDto result = debtService.cancelDebt(debtId);

        assertThat(result.status()).isEqualTo(DebtStatus.CANCELLED);
        verify(debtRepository).save(debt);
    }

    @Test
    @DisplayName("Candidate transactions: reference date is strictly createdAt in IST with 7-day lookback, ignoring dueDate")
    void testCandidateTransactions_LookbackBoundary_CreatedAtReference() {
        UUID debtId = UUID.randomUUID();
        LocalDate refDate = LocalDate.of(2026, 10, 15);
        Instant createdAtInstant = refDate.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();

        // Due date is set 30 days in future; should NOT affect the lookback reference date
        Debt debt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("1000.00"),
                LocalDate.of(2026, 11, 15), "Lunch loan");
        debt.setId(debtId);
        debt.setCreatedAt(createdAtInstant);

        LocalDate exactly7DaysBefore = refDate.minusDays(7); // 2026-10-08
        LocalDate eightDaysBefore = refDate.minusDays(8);    // 2026-10-07
        LocalDate sameDay = refDate;                         // 2026-10-15

        Transaction tx7Days = new Transaction(testUser, new BigDecimal("1000.00"), "Repayment exactly 7d before",
                TransactionCategory.OTHER, TransactionType.INCOME, exactly7DaysBefore, "");
        Transaction tx8Days = new Transaction(testUser, new BigDecimal("1000.00"), "Repayment 8d before",
                TransactionCategory.OTHER, TransactionType.INCOME, eightDaysBefore, "");
        Transaction txSameDay = new Transaction(testUser, new BigDecimal("1000.00"), "Repayment same day",
                TransactionCategory.OTHER, TransactionType.INCOME, sameDay, "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), eq(exactly7DaysBefore)))
                .thenReturn(List.of(txSameDay, tx7Days, tx8Days));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        // Verify repository query used refDate.minusDays(7) and NOT dueDate
        verify(transactionRepository).findCandidateCreditsForDebt(userId, exactly7DaysBefore);
        verify(transactionRepository, never()).findCandidateDebitsForDebt(any(), any());

        // Boundary assertion: exactly 7 days before and same day qualify; 8 days before does not qualify
        List<UUID> candidateIds = candidates.stream().map(DebtCandidateTransactionDto::id).toList();
        assertThat(candidateIds)
                .contains(txSameDay.getId(), tx7Days.getId())
                .doesNotContain(tx8Days.getId());
    }

    @Test
    @DisplayName("Candidate transactions: OWED_TO_ME queries credits and I_OWE queries debits")
    void testCandidateTransactions_DirectionFilters() {
        UUID owedDebtId = UUID.randomUUID();
        Debt owedDebt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        owedDebt.setId(owedDebtId);

        when(debtRepository.findByIdAndUserId(owedDebtId, userId)).thenReturn(Optional.of(owedDebt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any())).thenReturn(List.of());

        debtService.getCandidateTransactions(owedDebtId);
        verify(transactionRepository).findCandidateCreditsForDebt(eq(userId), any());
        verify(transactionRepository, never()).findCandidateDebitsForDebt(eq(userId), any());

        UUID iOweDebtId = UUID.randomUUID();
        Debt iOweDebt = new Debt(testUser, "Neha", DebtType.I_OWE, new BigDecimal("500.00"), null, null);
        iOweDebt.setId(iOweDebtId);

        when(debtRepository.findByIdAndUserId(iOweDebtId, userId)).thenReturn(Optional.of(iOweDebt));
        when(transactionRepository.findCandidateDebitsForDebt(eq(userId), any())).thenReturn(List.of());

        debtService.getCandidateTransactions(iOweDebtId);
        verify(transactionRepository).findCandidateDebitsForDebt(eq(userId), any());
    }

    @Test
    @DisplayName("Candidate transactions: transactions already linked to any debt are excluded")
    void testCandidateTransactions_ExcludesAlreadyLinkedTransactions() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);

        Transaction txLinked = new Transaction(testUser, new BigDecimal("1000.00"), "Already linked tx",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        Transaction txAvailable = new Transaction(testUser, new BigDecimal("1000.00"), "Available tx",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of(txLinked.getId()));
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any()))
                .thenReturn(List.of(txLinked, txAvailable));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo(txAvailable.getId());
    }

    @Test
    @DisplayName("Candidate transactions: ranking orders by match confidence and marks top match")
    void testCandidateTransactions_RankingAndTopMatch() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rohan Sharma", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);

        Transaction txExactAndName = new Transaction(testUser, new BigDecimal("1000.00"), "Rohan lunch refund",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        Transaction txExactAmount = new Transaction(testUser, new BigDecimal("1000.00"), "Unknown sender credit",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        Transaction txCloseAmount = new Transaction(testUser, new BigDecimal("980.00"), "Rohan dinner share",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        Transaction txGeneric = new Transaction(testUser, new BigDecimal("150.00"), "Grocery refund",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any()))
                .thenReturn(List.of(txGeneric, txCloseAmount, txExactAmount, txExactAndName));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).hasSize(4);
        // Top ranked should be exact amount + counterparty match -> isStrongMatch is true
        assertThat(candidates.get(0).id()).isEqualTo(txExactAndName.getId());
        assertThat(candidates.get(0).isTopMatch()).isTrue();
        assertThat(candidates.get(0).isStrongMatch()).isTrue();
        assertThat(candidates.get(0).exactAmount()).isTrue();
        assertThat(candidates.get(0).matchReason()).contains("Exact amount and counterparty match");

        // Second should be exact amount (not strong match because no counterparty)
        assertThat(candidates.get(1).id()).isEqualTo(txExactAmount.getId());
        assertThat(candidates.get(1).isTopMatch()).isFalse();
        assertThat(candidates.get(1).isStrongMatch()).isFalse();
        assertThat(candidates.get(1).exactAmount()).isTrue();

        // Third should be close amount + counterparty (not strong match because amount is not exact)
        assertThat(candidates.get(2).id()).isEqualTo(txCloseAmount.getId());
        assertThat(candidates.get(2).isTopMatch()).isFalse();
        assertThat(candidates.get(2).isStrongMatch()).isFalse();
        assertThat(candidates.get(2).exactAmount()).isFalse();

        // Fourth should be generic candidate
        assertThat(candidates.get(3).id()).isEqualTo(txGeneric.getId());
        assertThat(candidates.get(3).isTopMatch()).isFalse();
        assertThat(candidates.get(3).isStrongMatch()).isFalse();
        assertThat(candidates.get(3).exactAmount()).isFalse();
    }

    @Test
    @DisplayName("Candidate transactions: foreign or non-existent debt ID returns 404 NOT_FOUND")
    void testCandidateTransactions_ForeignDebtIdReturnsNotFound() {
        UUID foreignDebtId = UUID.randomUUID();
        when(debtRepository.findByIdAndUserId(foreignDebtId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtService.getCandidateTransactions(foreignDebtId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        verify(transactionRepository, never()).findCandidateCreditsForDebt(any(), any());
        verify(transactionRepository, never()).findCandidateDebitsForDebt(any(), any());
    }

    @Test
    @DisplayName("Settle debt: non-existent transaction returns 404 NOT_FOUND")
    void testSettleDebt_NonExistentTransactionReturnsNotFound() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rahul", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);
        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        UUID missingTxId = UUID.randomUUID();
        when(transactionRepository.findById(missingTxId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtService.settleDebt(debtId, new SettleDebtRequest(missingTxId)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Candidate transactions: no match returns empty list")
    void testCandidateTransactions_NoMatch_ReturnsEmptyList() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(userId, debtId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any())).thenReturn(List.of());

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);
        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("Candidate transactions: wrong direction - I_OWE queries debits and ignores credits")
    void testCandidateTransactions_WrongDirection_IOweQueriesDebits() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Landlord", DebtType.I_OWE, new BigDecimal("15000.00"), null, null);
        debt.setId(debtId);

        Transaction debitTx = new Transaction(testUser, new BigDecimal("15000.00"), "Rent payment",
                TransactionCategory.OTHER, TransactionType.EXPENSE, LocalDate.now(), "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(userId, debtId)).thenReturn(List.of());
        when(transactionRepository.findCandidateDebitsForDebt(eq(userId), any())).thenReturn(List.of(debitTx));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo(debitTx.getId());
        verify(transactionRepository).findCandidateDebitsForDebt(eq(userId), any());
        verify(transactionRepository, never()).findCandidateCreditsForDebt(any(), any());
    }

    @Test
    @DisplayName("Candidate transactions: amount mismatch scores low and does not flag top match")
    void testCandidateTransactions_AmountMismatch() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Priya", DebtType.OWED_TO_ME, new BigDecimal("5000.00"), null, null);
        debt.setId(debtId);

        Transaction txSmall = new Transaction(testUser, new BigDecimal("50.00"), "Tea",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(userId, debtId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any())).thenReturn(List.of(txSmall));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).isTopMatch()).isFalse();
        assertThat(candidates.get(0).isStrongMatch()).isFalse();
        assertThat(candidates.get(0).score()).isLessThan(3000);
    }

    @Test
    @DisplayName("Candidate transactions: settled without link - within 60 days qualifies without name match")
    void testCandidateTransactions_SettledWithoutLink_Within60DaysQualifies() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Arun Kumar", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), null, null);
        debt.setId(debtId);
        debt.settle(null); // Settled without link

        LocalDate settledDate = debt.getSettledAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
        LocalDate txDate = settledDate.plusDays(45); // Within 60 days
        Transaction txWithin60Days = new Transaction(testUser, new BigDecimal("2000.00"), "Unrelated narration",
                TransactionCategory.OTHER, TransactionType.INCOME, txDate, "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(userId, debtId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any())).thenReturn(List.of(txWithin60Days));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo(txWithin60Days.getId());
    }

    @Test
    @DisplayName("Candidate transactions: settled without link - outside 60 days without name match is excluded")
    void testCandidateTransactions_SettledWithoutLink_Outside60DaysWithoutNameMatchExcluded() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Arun Kumar", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), null, null);
        debt.setId(debtId);
        debt.settle(null);

        LocalDate settledDate = debt.getSettledAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
        LocalDate txDate = settledDate.plusDays(65); // Beyond 60 days, no name match
        Transaction txLate = new Transaction(testUser, new BigDecimal("2000.00"), "Unrelated Narration",
                TransactionCategory.OTHER, TransactionType.INCOME, txDate, "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(userId, debtId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any())).thenReturn(List.of(txLate));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("Candidate transactions: settled without link - outside 60 days WITH name match qualifies")
    void testCandidateTransactions_SettledWithoutLink_Outside60DaysWithNameMatchQualifies() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Arun Kumar", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), null, null);
        debt.setId(debtId);
        debt.settle(null);

        LocalDate settledDate = debt.getSettledAt().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
        LocalDate txDate = settledDate.plusDays(70); // Beyond 60 days, but counterparty matches
        Transaction txLateWithMatch = new Transaction(testUser, new BigDecimal("2000.00"), "Transfer from Arun Kumar",
                TransactionCategory.OTHER, TransactionType.INCOME, txDate, "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(userId, debtId)).thenReturn(List.of());
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any())).thenReturn(List.of(txLateWithMatch));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo(txLateWithMatch.getId());
    }

    @Test
    @DisplayName("Candidate transactions: dismissed transaction IDs are filtered out")
    void testCandidateTransactions_ExcludesDismissedTransactions() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Arun", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), null, null);
        debt.setId(debtId);

        Transaction txDismissed = new Transaction(testUser, new BigDecimal("2000.00"), "Arun repayment",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        Transaction txActive = new Transaction(testUser, new BigDecimal("2000.00"), "Arun second try",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(debtRepository.findLinkedTransactionIdsByUserId(userId)).thenReturn(List.of());
        when(dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(userId, debtId))
                .thenReturn(List.of(txDismissed.getId()));
        when(transactionRepository.findCandidateCreditsForDebt(eq(userId), any()))
                .thenReturn(List.of(txDismissed, txActive));

        List<DebtCandidateTransactionDto> candidates = debtService.getCandidateTransactions(debtId);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).id()).isEqualTo(txActive.getId());
    }

    @Test
    @DisplayName("Dismiss candidate persists dismissal entry")
    void testDismissCandidate_PersistsDismissal() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Arun", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), null, null);
        debt.setId(debtId);

        Transaction tx = new Transaction(testUser, new BigDecimal("2000.00"), "Wrong credit",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        UUID txId = tx.getId();

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(dismissalRepository.existsByUserIdAndDebtIdAndTransactionId(userId, debtId, txId)).thenReturn(false);

        debtService.dismissCandidate(debtId, txId);

        verify(dismissalRepository).save(any(DebtMatchDismissal.class));
    }

    @Test
    @DisplayName("Dismiss candidate rejects foreign transaction (user isolation)")
    void testDismissCandidate_UserIsolation() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Arun", DebtType.OWED_TO_ME, new BigDecimal("2000.00"), null, null);
        debt.setId(debtId);

        User foreignUser = new User("other@example.com", "hash", "Other", "Asia/Kolkata");
        Transaction foreignTx = new Transaction(foreignUser, new BigDecimal("2000.00"), "Foreign credit",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        UUID txId = foreignTx.getId();

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(foreignTx));

        assertThatThrownBy(() -> debtService.dismissCandidate(debtId, txId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(dismissalRepository, never()).save(any());
    }

    @Test
    @DisplayName("Settle debt throws BAD_REQUEST when called on already settled debt")
    void testSettleDebt_RejectsAlreadySettledDebt() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Arun", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);
        debt.settle(null);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        assertThatThrownBy(() -> debtService.settleDebt(debtId, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((ApiException) ex).getCode()).isEqualTo("DEBT_ALREADY_SETTLED");
                });
    }

    @Test
    @DisplayName("Link transaction to settled debt: links transaction and reclassifies as TRANSFER")
    void testLinkTransactionToSettledDebt_SuccessAndReclassification() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Kavita", DebtType.OWED_TO_ME, new BigDecimal("3000.00"), null, null);
        debt.setId(debtId);
        debt.settle(null);

        Transaction tx = new Transaction(testUser, new BigDecimal("3000.00"), "Kavita repayment",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        UUID txId = tx.getId();

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));
        when(debtRepository.save(any(Debt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        DebtDto result = debtService.linkTransactionToSettledDebt(debtId, new SettleDebtRequest(txId));

        assertThat(result.linkedTransactionId()).isEqualTo(txId);
        assertThat(tx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(tx.getTransferDirection()).isEqualTo(TransferDirection.IN);
        assertThat(tx.getIncomeTier()).isEqualTo(IncomeTier.TIER_3_REFUND);
        assertThat(tx.getCategory()).isEqualTo(TransactionCategory.OTHER);
    }

    @Test
    @DisplayName("Link transaction to settled debt: rejects ACTIVE debt and already linked debt")
    void testLinkTransactionToSettledDebt_RejectsActiveOrAlreadyLinked() {
        UUID activeDebtId = UUID.randomUUID();
        Debt activeDebt = new Debt(testUser, "Kavita", DebtType.OWED_TO_ME, new BigDecimal("3000.00"), null, null);
        activeDebt.setId(activeDebtId);

        when(debtRepository.findByIdAndUserId(activeDebtId, userId)).thenReturn(Optional.of(activeDebt));

        assertThatThrownBy(() -> debtService.linkTransactionToSettledDebt(activeDebtId, new SettleDebtRequest(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((ApiException) ex).getCode()).isEqualTo("DEBT_NOT_SETTLED");
                });

        UUID linkedDebtId = UUID.randomUUID();
        Debt alreadyLinkedDebt = new Debt(testUser, "Kavita", DebtType.OWED_TO_ME, new BigDecimal("3000.00"), null, null);
        alreadyLinkedDebt.setId(linkedDebtId);
        Transaction dummy = new Transaction(testUser, new BigDecimal("3000.00"), "Repayment",
                TransactionCategory.OTHER, TransactionType.TRANSFER, LocalDate.now(), "");
        alreadyLinkedDebt.settle(dummy);

        when(debtRepository.findByIdAndUserId(linkedDebtId, userId)).thenReturn(Optional.of(alreadyLinkedDebt));

        assertThatThrownBy(() -> debtService.linkTransactionToSettledDebt(linkedDebtId, new SettleDebtRequest(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((ApiException) ex).getCode()).isEqualTo("DEBT_ALREADY_LINKED");
                });
    }

    @Test
    @DisplayName("Link transaction to settled debt: rejects foreign user transaction")
    void testLinkTransactionToSettledDebt_UserIsolation() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Kavita", DebtType.OWED_TO_ME, new BigDecimal("3000.00"), null, null);
        debt.setId(debtId);
        debt.settle(null);

        User foreignUser = new User("foreign@example.com", "hash", "Foreign", "Asia/Kolkata");
        Transaction foreignTx = new Transaction(foreignUser, new BigDecimal("3000.00"), "Repayment",
                TransactionCategory.OTHER, TransactionType.INCOME, LocalDate.now(), "");
        UUID txId = foreignTx.getId();

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(transactionRepository.findById(txId)).thenReturn(Optional.of(foreignTx));

        assertThatThrownBy(() -> debtService.linkTransactionToSettledDebt(debtId, new SettleDebtRequest(txId)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Record payment and settle: OWED_TO_ME creates TRANSFER-IN with refund tier and settles debt")
    void testRecordPaymentAndSettle_SuccessOwedToMe() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("2500.00"), null, null);
        debt.setId(debtId);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(debtRepository.save(any(Debt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.spendsense.debt.dto.RecordPaymentAndSettleRequest request = new com.spendsense.debt.dto.RecordPaymentAndSettleRequest(
                new BigDecimal("2500.00"),
                LocalDate.now(ZoneId.of("Asia/Kolkata")),
                null,
                "Got money back via cash"
        );

        DebtDto result = debtService.recordPaymentAndSettle(debtId, request);

        assertThat(result.status()).isEqualTo(DebtStatus.SETTLED);
        assertThat(result.linkedTransactionId()).isNotNull();

        org.mockito.ArgumentCaptor<Transaction> txCaptor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction savedTx = txCaptor.getValue();

        assertThat(savedTx.getAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(savedTx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(savedTx.getTransferDirection()).isEqualTo(TransferDirection.IN);
        assertThat(savedTx.getCategory()).isEqualTo(TransactionCategory.OTHER);
        assertThat(savedTx.getIncomeTier()).isEqualTo(IncomeTier.TIER_3_REFUND);
        assertThat(savedTx.isIncomeVerified()).isFalse();
        assertThat(savedTx.getSourceType()).isEqualTo(com.spendsense.transaction.TransactionSourceType.MANUAL);
        assertThat(savedTx.getSourceReference()).isEqualTo("debt:repayment:" + debtId);
        assertThat(savedTx.getNotes()).isEqualTo("Got money back via cash");
    }

    @Test
    @DisplayName("Record payment and settle: I_OWE creates TRANSFER-OUT and settles debt")
    void testRecordPaymentAndSettle_SuccessIOwe() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Landlord", DebtType.I_OWE, new BigDecimal("12000.00"), null, null);
        debt.setId(debtId);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(debtRepository.save(any(Debt.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.spendsense.debt.dto.RecordPaymentAndSettleRequest request = new com.spendsense.debt.dto.RecordPaymentAndSettleRequest(
                new BigDecimal("12000.00"),
                LocalDate.now(ZoneId.of("Asia/Kolkata")),
                null,
                null
        );

        DebtDto result = debtService.recordPaymentAndSettle(debtId, request);

        assertThat(result.status()).isEqualTo(DebtStatus.SETTLED);

        org.mockito.ArgumentCaptor<Transaction> txCaptor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction savedTx = txCaptor.getValue();

        assertThat(savedTx.getAmount()).isEqualByComparingTo(new BigDecimal("12000.00"));
        assertThat(savedTx.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(savedTx.getTransferDirection()).isEqualTo(TransferDirection.OUT);
        assertThat(savedTx.getCategory()).isEqualTo(TransactionCategory.OTHER);
        assertThat(savedTx.getIncomeTier()).isNull();
        assertThat(savedTx.getSourceType()).isEqualTo(com.spendsense.transaction.TransactionSourceType.MANUAL);
        assertThat(savedTx.getSourceReference()).isEqualTo("debt:repayment:" + debtId);
        assertThat(savedTx.getNotes()).isEqualTo("Payment to Landlord");
    }

    @Test
    @DisplayName("Record payment and settle: validates account belongs to user")
    void testRecordPaymentAndSettle_AccountValidation() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);

        UUID accountId = UUID.randomUUID();
        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));
        when(accountRepository.findByIdAndUserId(accountId, userId)).thenReturn(Optional.empty());

        com.spendsense.debt.dto.RecordPaymentAndSettleRequest request = new com.spendsense.debt.dto.RecordPaymentAndSettleRequest(
                new BigDecimal("1000.00"),
                LocalDate.now(ZoneId.of("Asia/Kolkata")),
                accountId,
                null
        );

        assertThatThrownBy(() -> debtService.recordPaymentAndSettle(debtId, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(((ApiException) ex).getCode()).isEqualTo("ACCOUNT_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("Record payment and settle: rejects invalid amount or future date")
    void testRecordPaymentAndSettle_ValidationRules() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        // Negative/zero amount
        com.spendsense.debt.dto.RecordPaymentAndSettleRequest zeroAmount = new com.spendsense.debt.dto.RecordPaymentAndSettleRequest(
                BigDecimal.ZERO,
                LocalDate.now(ZoneId.of("Asia/Kolkata")),
                null,
                null
        );
        assertThatThrownBy(() -> debtService.recordPaymentAndSettle(debtId, zeroAmount))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_AMOUNT"));

        // Future date
        com.spendsense.debt.dto.RecordPaymentAndSettleRequest futureDate = new com.spendsense.debt.dto.RecordPaymentAndSettleRequest(
                new BigDecimal("500.00"),
                LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1),
                null,
                null
        );
        assertThatThrownBy(() -> debtService.recordPaymentAndSettle(debtId, futureDate))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("INVALID_DATE"));
    }

    @Test
    @DisplayName("Record payment and settle: rejects already settled debt or missing debt")
    void testRecordPaymentAndSettle_DebtStateValidation() {
        UUID debtId = UUID.randomUUID();
        Debt debt = new Debt(testUser, "Rohan", DebtType.OWED_TO_ME, new BigDecimal("1000.00"), null, null);
        debt.setId(debtId);
        debt.settle(null);

        when(debtRepository.findByIdAndUserId(debtId, userId)).thenReturn(Optional.of(debt));

        com.spendsense.debt.dto.RecordPaymentAndSettleRequest request = new com.spendsense.debt.dto.RecordPaymentAndSettleRequest(
                new BigDecimal("1000.00"),
                LocalDate.now(ZoneId.of("Asia/Kolkata")),
                null,
                null
        );

        assertThatThrownBy(() -> debtService.recordPaymentAndSettle(debtId, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("DEBT_ALREADY_SETTLED"));

        UUID missingDebtId = UUID.randomUUID();
        when(debtRepository.findByIdAndUserId(missingDebtId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> debtService.recordPaymentAndSettle(missingDebtId, request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
