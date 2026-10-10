package com.spendsense.categorization.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.categorization.CategorizationResult;
import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.categorization.TransactionNormalizer;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionSourceType;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class HistoricalReclassificationServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CategorizationService categorizationService;

    private TransactionNormalizer normalizer;
    private HistoricalReclassificationService reclassificationService;

    private User user;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
        reclassificationService = new HistoricalReclassificationService(
                transactionRepository,
                normalizer,
                categorizationService
        );
        user = new User("user@example.com", "hash", "Test User", "Asia/Kolkata");
    }

    @Test
    @DisplayName("Dry run does NOT save or modify any database transactions")
    void dryRunDoesNotMutateDatabase() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        Transaction tx1 = new Transaction(
                user, BigDecimal.valueOf(50),
                "WDL TFR UPI/DR/625481840735/Rapido/UT IB/rapido5223/UPI 0097694162092 AT 03117 VERSOVA (MUMBAI)",
                null,
                TransactionCategory.BILLS,
                TransactionType.EXPENSE,
                null,
                LocalDate.of(2026, 9, 10),
                null
        );
        tx1.setSourceType(TransactionSourceType.STATEMENT_IMPORT);

        when(transactionRepository.findByTransactionDateBetween(start, end))
                .thenReturn(List.of(tx1));

        when(categorizationService.categorize(any())).thenReturn(
                CategorizationResult.fromRule(
                        TransactionCategory.TRAVEL,
                        1.0,
                        ConfidenceLevel.HIGH,
                        "RULE_RIDE_HAILING_TRAVEL",
                        "Matched Rapido bike ride",
                        "rapido"
                )
        );

        HistoricalReclassificationReport report = reclassificationService.runDryRun(start, end);

        assertThat(report.totalInspected()).isEqualTo(1);
        assertThat(report.totalChanged()).isEqualTo(1);
        assertThat(report.transitions()).containsEntry("BILLS -> TRAVEL", 1L);
        assertThat(report.sampleChangedItems()).hasSize(1);
        assertThat(report.sampleChangedItems().get(0).proposedDisplayMerchant()).isEqualTo("Rapido");
        assertThat(report.sampleChangedItems().get(0).proposedCategory()).isEqualTo(TransactionCategory.TRAVEL);

        // Verification: Database save must NEVER be called during a dry run
        verify(transactionRepository, never()).save(any());
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Preserves manual user corrections and does not reclassify them")
    void preservesManualUserCorrections() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        Transaction manualTx = new Transaction(
                user, BigDecimal.valueOf(120),
                "Starbucks Coffee",
                "Work meeting",
                TransactionCategory.FOOD,
                TransactionType.EXPENSE,
                null,
                LocalDate.of(2026, 9, 12),
                null
        );
        manualTx.setSourceType(TransactionSourceType.MANUAL);

        when(transactionRepository.findByTransactionDateBetween(start, end))
                .thenReturn(List.of(manualTx));

        HistoricalReclassificationReport report = reclassificationService.runDryRun(start, end);

        assertThat(report.totalInspected()).isEqualTo(1);
        assertThat(report.totalChanged()).isEqualTo(0);
        assertThat(report.totalPreservedManual()).isEqualTo(1);
        assertThat(report.proposedDistribution()).containsEntry(TransactionCategory.FOOD, 1L);

        // Categorization service should not even be called for manual transactions
        verify(categorizationService, never()).categorize(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Dry run is idempotent: running twice yields identical metrics")
    void dryRunIsIdempotent() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        Transaction tx = new Transaction(
                user, BigDecimal.valueOf(250),
                "Blinkit",
                null,
                TransactionCategory.BILLS,
                TransactionType.EXPENSE,
                null,
                LocalDate.of(2026, 9, 5),
                null
        );
        tx.setSourceType(TransactionSourceType.STATEMENT_IMPORT);

        when(transactionRepository.findByTransactionDateBetween(start, end))
                .thenReturn(List.of(tx));

        when(categorizationService.categorize(any())).thenReturn(
                CategorizationResult.fromRule(
                        TransactionCategory.FOOD,
                        1.0,
                        ConfidenceLevel.HIGH,
                        "RULE_QUICK_COMMERCE_FOOD",
                        "Matched Blinkit",
                        "blinkit"
                )
        );

        HistoricalReclassificationReport run1 = reclassificationService.runDryRun(start, end);
        HistoricalReclassificationReport run2 = reclassificationService.runDryRun(start, end);

        assertThat(run1.totalInspected()).isEqualTo(run2.totalInspected());
        assertThat(run1.totalChanged()).isEqualTo(run2.totalChanged());
        assertThat(run1.transitions()).isEqualTo(run2.transitions());
        assertThat(run1.proposedDistribution()).isEqualTo(run2.proposedDistribution());
    }
}
