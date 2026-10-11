package com.spendsense.debt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.spendsense.common.exception.ApiException;
import com.spendsense.debt.dto.CreateDebtRequest;
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
import java.time.LocalDate;
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

    private DebtService debtService;
    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        debtService = new DebtService(debtRepository, transactionRepository, userRepository, currentUser);

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
}
