package com.spendsense.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.analytics.dto.CategoryAnalysis;
import com.spendsense.analytics.dto.CategoryBreakdownResponse;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/spendsense?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
        "spring.datasource.username=root",
        "spring.datasource.password=Harshit#$04",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
class September2026AnalyticsReconciliationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("Reconcile user-scoped analytics (88 expenses) against table-wide dataset (310 transactions)")
    void reconcileUserAnalyticsAndTableWideState() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        // 1. Table-wide dataset verification: 310 transactions across 7 categories
        List<Transaction> allSeptemberTx = transactionRepository.findByTransactionDateBetween(start, end);
        assertThat(allSeptemberTx).hasSize(310);

        Map<TransactionCategory, Long> tableCategoryCounts = allSeptemberTx.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.counting()));

        assertThat(tableCategoryCounts.get(TransactionCategory.TRAVEL)).isEqualTo(66L);
        assertThat(tableCategoryCounts.get(TransactionCategory.FOOD)).isEqualTo(8L);
        assertThat(tableCategoryCounts.get(TransactionCategory.OTHER)).isEqualTo(232L);
        assertThat(tableCategoryCounts.get(TransactionCategory.BILLS)).isEqualTo(1L);
        assertThat(tableCategoryCounts.get(TransactionCategory.SHOPPING)).isEqualTo(1L);
        assertThat(tableCategoryCounts.get(TransactionCategory.ENTERTAINMENT)).isEqualTo(1L);
        assertThat(tableCategoryCounts.get(TransactionCategory.HEALTH)).isEqualTo(1L);

        // 2. User-scoped verification for iharshsoni@gmail.com
        User user = userRepository.findByEmail("iharshsoni@gmail.com").orElseThrow();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new SpendSenseUserPrincipal(user), null, java.util.List.of())
        );

        CategoryBreakdownResponse breakdown = analyticsService.getCategoryBreakdown(start, end);

        // Total expense transactions must be exactly 88
        assertThat(breakdown.totalTransactions()).isEqualTo(88L);
        assertThat(breakdown.autoClassifiedCount()).isEqualTo(23L);
        assertThat(breakdown.requiresReviewCount()).isEqualTo(65L);
        assertThat(breakdown.autoClassifiedPercentage()).isEqualByComparingTo("26.1");
        assertThat(breakdown.requiresReviewPercentage()).isEqualByComparingTo("73.9");

        // Category breakdown sum verification: 65 (OTHER) + 22 (TRAVEL) + 1 (FOOD) = 88
        long categorySum = breakdown.categories().stream()
                .mapToLong(CategoryAnalysis::transactionCount)
                .sum();
        assertThat(categorySum).isEqualTo(88L);

        // Individual category check
        var other = breakdown.categories().stream().filter(c -> c.category() == TransactionCategory.OTHER).findFirst().orElseThrow();
        assertThat(other.transactionCount()).isEqualTo(65L);
        assertThat(other.requiresReviewCount()).isEqualTo(65L);

        var travel = breakdown.categories().stream().filter(c -> c.category() == TransactionCategory.TRAVEL).findFirst().orElseThrow();
        assertThat(travel.transactionCount()).isEqualTo(22L);
        assertThat(travel.requiresReviewCount()).isEqualTo(0L);

        var food = breakdown.categories().stream().filter(c -> c.category() == TransactionCategory.FOOD).findFirst().orElseThrow();
        assertThat(food.transactionCount()).isEqualTo(1L);
        assertThat(food.requiresReviewCount()).isEqualTo(0L);
    }
}
