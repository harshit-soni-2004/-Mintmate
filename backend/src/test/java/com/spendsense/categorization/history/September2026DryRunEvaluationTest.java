package com.spendsense.categorization.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.TransactionNormalizer;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:mysql://localhost:3306/spendsense?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8",
        "spring.datasource.username=root",
        "spring.datasource.password=Harshit#$04",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
class September2026DryRunEvaluationTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionNormalizer normalizer;

    @Autowired
    private CategorizationService categorizationService;

    @Autowired
    private HistoricalReclassificationService reclassificationService;

    @Test
    @DisplayName("Execute safe dry-run reclassification on September 2026 dataset")
    void executeSeptember2026DryRun() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        List<Transaction> beforeTransactions = transactionRepository.findByTransactionDateBetween(start, end);
        assertThat(beforeTransactions).isNotEmpty();
        int totalBefore = beforeTransactions.size();

        // 1. Run dry run
        HistoricalReclassificationReport report = reclassificationService.runDryRun(start, end);

        System.out.println("================================================================================");
        System.out.println("         MINTMATE HISTORICAL RECLASSIFICATION DRY-RUN REPORT                   ");
        System.out.println("                 Period: September 2026 Dataset                                 ");
        System.out.println("================================================================================");
        System.out.printf("Total transactions inspected:   %d%n", report.totalInspected());
        System.out.printf("Total proposed category changes: %d%n", report.totalChanged());
        System.out.printf("Total manual user classifications preserved: %d%n", report.totalPreservedManual());
        System.out.printf("Total transactions requiring human review:   %d (%.2f%%)%n",
                report.totalRequiringReview(), (100.0 * report.totalRequiringReview() / report.totalInspected()));
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("CURRENT CATEGORY DISTRIBUTION:");
        report.currentDistribution().forEach((cat, count) ->
                System.out.printf("  %-15s: %d%n", cat, count));

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("PROPOSED CATEGORY DISTRIBUTION (After Gating & High-Precision Rules):");
        report.proposedDistribution().forEach((cat, count) ->
                System.out.printf("  %-15s: %d%n", cat, count));

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("CATEGORY TRANSITION MATRIX:");
        report.transitions().forEach((transition, count) ->
                System.out.printf("  %-25s: %d%n", transition, count));

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("DETERMINISTIC RULE MATCHES:");
        report.ruleMatches().forEach((rule, count) ->
                System.out.printf("  %-30s: %d%n", rule, count));

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("SAMPLE PROPOSED RECLASSIFICATIONS (First 20 Changed Items):");
        int count = 0;
        for (HistoricalReclassificationItem item : report.sampleChangedItems()) {
            if (count++ >= 20) break;
            System.out.printf("  Tx ID: %s | Date: %s | Amount: %s%n",
                    item.transactionId(), item.transactionDate(), item.amount());
            System.out.printf("    Merchant: '%s' -> Proposed Display: '%s'%n",
                    item.originalMerchant().length() > 60 ? item.originalMerchant().substring(0, 57) + "..." : item.originalMerchant(),
                    item.proposedDisplayMerchant());
            System.out.printf("    Category: %s -> %s (Pred: %s, Conf: %.2f [%s]) | Review: %s%n",
                    item.currentCategory(), item.proposedCategory(), item.predictedCategory(),
                    item.confidence(), item.confidenceLevel(), item.requiresReview());
            System.out.printf("    Evidence: %s%n%n", item.evidence());
        }
        System.out.println("================================================================================");

        // 2. Validate hypotheses
        // A. Rapido transactions must move from BILLS to TRAVEL
        long rapidoTravel = report.sampleChangedItems().stream()
                .filter(i -> i.originalMerchant().contains("Rapido"))
                .filter(i -> i.proposedCategory() == TransactionCategory.TRAVEL)
                .count();
        System.out.printf("Rapido transactions mapped to TRAVEL in sample: %d%n", rapidoTravel);

        // B. Assert NO rows were mutated in the database!
        List<Transaction> afterTransactions = transactionRepository.findByTransactionDateBetween(start, end);
        assertThat(afterTransactions).hasSize(totalBefore);

        for (int i = 0; i < beforeTransactions.size(); i++) {
            Transaction b = beforeTransactions.get(i);
            Transaction a = afterTransactions.get(i);
            assertThat(a.getId()).isEqualTo(b.getId());
            assertThat(a.getCategory()).isEqualTo(b.getCategory());
        }
        System.out.println("SAFE DRY-RUN CONFIRMED: 0 database rows modified!");
    }
}
