package com.spendsense.categorization.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@SpringBootTest
@ActiveProfiles("dev")

class HistoricalReclassificationApplyAndRollbackTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private HistoricalReclassificationService reclassificationService;

    @Test
    @DisplayName("Verify rollback mechanism first, then apply reclassification for September 2026")
    void verifyRollbackAndApplySeptember2026() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        List<Transaction> initialTransactions = transactionRepository.findByTransactionDateBetweenOrderByTransactionDateAsc(start, end);
        assertThat(initialTransactions).isNotEmpty();
        int initialCount = initialTransactions.size();

        Map<UUID, TransactionCategory> initialCategories = initialTransactions.stream()
                .collect(Collectors.toMap(Transaction::getId, Transaction::getCategory));
        Map<UUID, Boolean> initialReviews = initialTransactions.stream()
                .collect(Collectors.toMap(Transaction::getId, Transaction::isRequiresReview));
        Map<UUID, String> initialMerchants = initialTransactions.stream()
                .collect(Collectors.toMap(Transaction::getId, Transaction::getMerchant));

        // ---------------------------------------------------------------------
        // STEP 1: TEST ROLLBACK MECHANISM
        // ---------------------------------------------------------------------
        System.out.println(">>> STEP 1: Testing Rollback & Recovery Mechanism...");
        HistoricalReclassificationApplyResult testApplyResult = reclassificationService.applyReclassification(start, end);
        assertThat(testApplyResult.backupFilePath()).isNotNull();
        assertThat(new File(testApplyResult.backupFilePath())).exists();

        // Rollback immediately to verify recovery
        int rolledBack = reclassificationService.rollbackReclassification(testApplyResult.backupFilePath());
        assertThat(rolledBack).isEqualTo(initialCount);

        // Verify database state is 100% restored
        List<Transaction> restoredTransactions = transactionRepository.findByTransactionDateBetweenOrderByTransactionDateAsc(start, end);
        for (Transaction tx : restoredTransactions) {
            assertThat(tx.getCategory()).isEqualTo(initialCategories.get(tx.getId()));
            assertThat(tx.isRequiresReview()).isEqualTo(initialReviews.get(tx.getId()));
            assertThat(tx.getMerchant()).isEqualTo(initialMerchants.get(tx.getId()));
        }
        System.out.println(">>> STEP 1 SUCCESS: Rollback restored 100% of rows to their exact previous state!");

        // ---------------------------------------------------------------------
        // STEP 2: APPLY RECLASSIFICATION TO SEPTEMBER 2026
        // ---------------------------------------------------------------------
        System.out.println(">>> STEP 2: Applying Reclassification to September 2026 Dataset...");
        HistoricalReclassificationApplyResult liveApplyResult = reclassificationService.applyReclassification(start, end);
        System.out.printf("Applied to %d transactions (%d updated). Backup saved to: %s%n",
                liveApplyResult.totalInspected(), liveApplyResult.totalUpdated(), liveApplyResult.backupFilePath());

        // ---------------------------------------------------------------------
        // STEP 3: DIRECT LIVE DATABASE QUERY & DISTRIBUTION VERIFICATION
        // ---------------------------------------------------------------------
        System.out.println(">>> STEP 3: Querying Live Database Directly for September 2026...");
        List<Transaction> postApplyTransactions = transactionRepository.findByTransactionDateBetweenOrderByTransactionDateAsc(start, end);

        Map<TransactionCategory, Long> liveDbDistribution = new EnumMap<>(TransactionCategory.class);
        long liveRequiringReview = 0;
        long liveRapidoInTravel = 0;
        long liveBlinkitInFood = 0;

        for (Transaction tx : postApplyTransactions) {
            liveDbDistribution.merge(tx.getCategory(), 1L, Long::sum);
            if (tx.isRequiresReview()) {
                liveRequiringReview++;
            }
            if (tx.getMerchant().equalsIgnoreCase("Rapido") && tx.getCategory() == TransactionCategory.TRAVEL) {
                liveRapidoInTravel++;
            }
            if (tx.getMerchant().equalsIgnoreCase("Blinkit") && tx.getCategory() == TransactionCategory.FOOD) {
                liveBlinkitInFood++;
            }
        }

        System.out.println("================================================================================");
        System.out.println("           ACTUAL LIVE DATABASE CATEGORY DISTRIBUTION (POST-APPLY)              ");
        System.out.println("================================================================================");
        liveDbDistribution.forEach((cat, count) ->
                System.out.printf("  %-15s: %d (%.2f%%)%n", cat, count, (100.0 * count / initialCount)));
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Total transactions in DB:     %d%n", postApplyTransactions.size());
        System.out.printf("Total marked requiresReview:  %d (%.2f%%)%n", liveRequiringReview, (100.0 * liveRequiringReview / initialCount));
        System.out.printf("Rapido in TRAVEL:             %d%n", liveRapidoInTravel);
        System.out.printf("Blinkit in FOOD:              %d%n", liveBlinkitInFood);
        System.out.println("================================================================================");

        assertThat(liveDbDistribution.get(TransactionCategory.TRAVEL)).isGreaterThanOrEqualTo(60L);
        assertThat(liveDbDistribution.get(TransactionCategory.BILLS)).isLessThanOrEqualTo(5L);
        assertThat(liveRapidoInTravel).isGreaterThanOrEqualTo(50L);
        assertThat(liveBlinkitInFood).isGreaterThanOrEqualTo(3L);
    }
}
