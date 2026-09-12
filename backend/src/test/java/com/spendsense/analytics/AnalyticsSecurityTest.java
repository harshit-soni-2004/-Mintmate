package com.spendsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsSecurityTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private CurrentUser currentUser;

    @Test
    void userCannotQueryAnotherUsersAnalytics() {
        User userA = new User("usera@example.com", "hash", "User A", "Asia/Kolkata");
        User userB = new User("userb@example.com", "hash", "User B", "Asia/Kolkata");

        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(userA));
        AnalyticsService service = new AnalyticsService(transactionRepository, currentUser);

        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);

        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(userA.getId(), TransactionType.INCOME, from, to))
                .thenReturn(new BigDecimal("1000.00"));
        when(transactionRepository.sumAmountByUserIdAndTypeAndDateBetween(userA.getId(), TransactionType.EXPENSE, from, to))
                .thenReturn(new BigDecimal("500.00"));

        service.getSummary(from, to);

        // Verify repository is ONLY invoked with authenticated userA's ID, never userB's
        verify(transactionRepository).sumAmountByUserIdAndTypeAndDateBetween(
                eq(userA.getId()), eq(TransactionType.INCOME), eq(from), eq(to));
        verify(transactionRepository).sumAmountByUserIdAndTypeAndDateBetween(
                eq(userA.getId()), eq(TransactionType.EXPENSE), eq(from), eq(to));
    }
}
