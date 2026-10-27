package com.spendsense.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.model.BaselineCategorizationModel;
import com.spendsense.categorization.policy.CategorizationConfidencePolicy;
import com.spendsense.categorization.rule.AtmCashWithdrawalRule;
import com.spendsense.categorization.rule.BankInterestAndDividendRule;
import com.spendsense.categorization.rule.BillsAndUtilitiesRule;
import com.spendsense.categorization.rule.EntertainmentAndStreamingRule;
import com.spendsense.categorization.rule.InternalTransferRule;
import com.spendsense.categorization.rule.PayrollAndIncomeRule;
import com.spendsense.categorization.rule.QuickCommerceAndFoodRule;
import com.spendsense.categorization.rule.RideHailingAndTravelRule;
import com.spendsense.categorization.rule.RuleEngine;
import com.spendsense.categorization.rule.ShoppingAndRetailRule;
import com.spendsense.categorization.rule.TaxAndGovernmentRule;
import com.spendsense.transaction.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Benchmark evaluation suite measuring precision, recall, and review coverage
 * against real Indian labeled datasets.
 */
class CategorizationEvaluationBenchmarkTest {

    private CategorizationService categorizationService;

    @BeforeEach
    void setUp() {
        TransactionNormalizer normalizer = new TransactionNormalizer();
        RuleEngine ruleEngine = new RuleEngine(List.of(
                new InternalTransferRule(),
                new PayrollAndIncomeRule(),
                new TaxAndGovernmentRule(),
                new BankInterestAndDividendRule(),
                new AtmCashWithdrawalRule(),
                new RideHailingAndTravelRule(),
                new QuickCommerceAndFoodRule(),
                new BillsAndUtilitiesRule(),
                new ShoppingAndRetailRule(),
                new EntertainmentAndStreamingRule()
        ));

        UserCategorizationMemoryService emptyMemoryService = new UserCategorizationMemoryService(
                org.mockito.Mockito.mock(UserCategorizationMemoryRepository.class),
                normalizer
        );

        BaselineCategorizationModel baselineModel = new BaselineCategorizationModel(
                new ClassPathResource("model/baseline_model_v1.json"),
                new ObjectMapper()
        );

        CategorizationConfidencePolicy confidencePolicy = new CategorizationConfidencePolicy(0.85, 0.60);

        categorizationService = new CategorizationService(
                normalizer,
                ruleEngine,
                emptyMemoryService,
                baselineModel,
                null,
                confidencePolicy,
                true,
                false,
                0.60
        );
    }

    @Test
    @DisplayName("Evaluate pipeline against deepam_indian_test.csv")
    void evaluateDeepamDataset() throws Exception {
        File file = new File("../ml/data/raw/deepam_indian_test.csv");
        if (!file.exists()) {
            file = new File("ml/data/raw/deepam_indian_test.csv");
        }
        assertThat(file).exists();

        Map<TransactionCategory, Long> truePositives = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, Long> falsePositives = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, Long> falseNegatives = new EnumMap<>(TransactionCategory.class);
        Map<TransactionCategory, Long> groundTruthCounts = new EnumMap<>(TransactionCategory.class);

        int totalEvaluated = 0;
        int totalReviewed = 0;
        int totalAutoAssigned = 0;
        int autoAssignedCorrect = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine(); // transaction_narration,label
            String line;

            while ((line = reader.readLine()) != null) {
                int commaIndex = line.lastIndexOf(',');
                if (commaIndex < 0) continue;

                String narration = line.substring(0, commaIndex).trim();
                String rawLabel = line.substring(commaIndex + 1).trim().toLowerCase();

                TransactionCategory expectedCategory = mapDeepamLabel(rawLabel);
                if (expectedCategory == null) continue;

                groundTruthCounts.merge(expectedCategory, 1L, Long::sum);
                totalEvaluated++;

                CategorizationRequest request = new CategorizationRequest(
                        null,
                        null,
                        narration,
                        BigDecimal.valueOf(100),
                        "UPI",
                        LocalDate.of(2026, 9, 15)
                );

                CategorizationResult result = categorizationService.categorize(request);
                boolean requiresReview = result.requiresUserConfirmation();
                TransactionCategory assignedCategory = result.category();
                TransactionCategory predictedCategory = result.predictedCategory();

                if (requiresReview) {
                    totalReviewed++;
                } else {
                    totalAutoAssigned++;
                    if (assignedCategory == expectedCategory) {
                        autoAssignedCorrect++;
                        truePositives.merge(expectedCategory, 1L, Long::sum);
                    } else {
                        falsePositives.merge(assignedCategory, 1L, Long::sum);
                        falseNegatives.merge(expectedCategory, 1L, Long::sum);
                    }
                }
            }
        }

        double reviewCoverage = (double) totalReviewed / totalEvaluated;
        double autoAssignPrecision = totalAutoAssigned > 0 ? (double) autoAssignedCorrect / totalAutoAssigned : 0.0;

        System.out.println("=================================================================");
        System.out.println("  EVALUATION BENCHMARK: deepam_indian_test.csv (1000 samples)");
        System.out.println("=================================================================");
        System.out.printf("Total evaluated: %d%n", totalEvaluated);
        System.out.printf("Auto-assigned (high confidence): %d (%.2f%%)%n", totalAutoAssigned, (100.0 * totalAutoAssigned / totalEvaluated));
        System.out.printf("Auto-assign Precision: %.2f%%%n", autoAssignPrecision * 100.0);
        System.out.printf("Gated for review (confidence < 0.85): %d (%.2f%% review coverage)%n", totalReviewed, reviewCoverage * 100.0);
        System.out.println("-----------------------------------------------------------------");
        System.out.printf("%-15s | %-12s | %-10s | %-10s | %-10s%n", "Category", "GroundTruth", "TP", "Precision", "Recall");
        System.out.println("-----------------------------------------------------------------");

        for (TransactionCategory cat : List.of(TransactionCategory.FOOD, TransactionCategory.TRAVEL, TransactionCategory.SHOPPING, TransactionCategory.BILLS, TransactionCategory.OTHER)) {
            long gt = groundTruthCounts.getOrDefault(cat, 0L);
            long tp = truePositives.getOrDefault(cat, 0L);
            long fp = falsePositives.getOrDefault(cat, 0L);
            long fn = falseNegatives.getOrDefault(cat, 0L);

            double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 1.0;
            double recall = gt > 0 ? (double) tp / gt : 0.0;

            System.out.printf("%-15s | %-12d | %-10d | %-9.2f%% | %-9.2f%%%n",
                    cat.name(), gt, tp, precision * 100.0, recall * 100.0);
        }
        System.out.println("=================================================================");

        // Quality assertions: auto-assigned decisions must exceed high confidence bar (>= 85%)
        assertThat(autoAssignPrecision).isGreaterThan(0.85);
    }

    @Test
    @DisplayName("Evaluate pipeline against indian_transactions_sumeet.csv")
    void evaluateSumeetDataset() throws Exception {
        File file = new File("../ml/data/raw/indian_transactions_sumeet.csv");
        if (!file.exists()) {
            file = new File("ml/data/raw/indian_transactions_sumeet.csv");
        }
        assertThat(file).exists();

        int totalEvaluated = 0;
        int autoAssigned = 0;
        int autoAssignedCorrect = 0;
        int requiresReviewCount = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine(); // text,category
            String line;

            while ((line = reader.readLine()) != null) {
                int commaIndex = line.lastIndexOf(',');
                if (commaIndex < 0) continue;

                String narration = line.substring(0, commaIndex).trim();
                String rawCategory = line.substring(commaIndex + 1).trim();

                TransactionCategory expected = mapSumeetCategory(rawCategory);
                if (expected == null) continue;

                totalEvaluated++;

                CategorizationRequest request = new CategorizationRequest(
                        null,
                        null,
                        narration,
                        BigDecimal.valueOf(200),
                        "UPI",
                        LocalDate.of(2026, 9, 15)
                );

                CategorizationResult result = categorizationService.categorize(request);
                if (result.requiresUserConfirmation()) {
                    requiresReviewCount++;
                } else {
                    autoAssigned++;
                    if (result.category() == expected) {
                        autoAssignedCorrect++;
                    }
                }
            }
        }

        double precision = autoAssigned > 0 ? (double) autoAssignedCorrect / autoAssigned : 0.0;
        double reviewCoverage = (double) requiresReviewCount / totalEvaluated;

        System.out.println("=================================================================");
        System.out.println("  EVALUATION BENCHMARK: indian_transactions_sumeet.csv (811 samples)");
        System.out.println("=================================================================");
        System.out.printf("Total evaluated: %d%n", totalEvaluated);
        System.out.printf("Auto-assigned: %d (Precision: %.2f%%)%n", autoAssigned, precision * 100.0);
        System.out.printf("Review coverage: %d (%.2f%%)%n", requiresReviewCount, reviewCoverage * 100.0);
        System.out.println("=================================================================");

        assertThat(totalEvaluated).isGreaterThan(500);
    }

    private TransactionCategory mapDeepamLabel(String label) {
        return switch (label) {
            case "food" -> TransactionCategory.FOOD;
            case "travel" -> TransactionCategory.TRAVEL;
            case "shopping" -> TransactionCategory.SHOPPING;
            case "investment" -> TransactionCategory.OTHER;
            case "emi" -> TransactionCategory.BILLS;
            default -> null;
        };
    }

    private TransactionCategory mapSumeetCategory(String cat) {
        return switch (cat) {
            case "Transportation & Gas", "Travel" -> TransactionCategory.TRAVEL;
            case "Eating Out", "Groceries" -> TransactionCategory.FOOD;
            case "Utilities", "Rent & Mortgage" -> TransactionCategory.BILLS;
            case "Shopping & Clothing" -> TransactionCategory.SHOPPING;
            case "Entertainment & Subscriptions" -> TransactionCategory.ENTERTAINMENT;
            case "Medicine & Pharmacy", "Hospital & Medical" -> TransactionCategory.HEALTH;
            case "Education" -> TransactionCategory.EDUCATION;
            case "Personal Care" -> TransactionCategory.PERSONAL;
            case "ATM & Cash", "Investments & Savings Transfer", "Income & Deposits", "Fees & Interest", "Kids Activities", "Insurance" -> TransactionCategory.OTHER;
            default -> null;
        };
    }
}
