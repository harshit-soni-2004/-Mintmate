package com.spendsense.assistant.proactive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.analytics.AnalyticsService;
import com.spendsense.analytics.AnomalyDetectionService;
import com.spendsense.analytics.InsightSeverity;
import com.spendsense.analytics.TrendDirection;
import com.spendsense.assistant.context.FinancialContextService;
import com.spendsense.assistant.context.model.CategoryProfile;
import com.spendsense.assistant.context.model.DataSufficiency;
import com.spendsense.assistant.context.model.DataSufficiencyProfile;
import com.spendsense.assistant.context.model.FinancialProfile;
import com.spendsense.budget.BudgetPeriod;
import com.spendsense.budget.BudgetService;
import com.spendsense.budget.BudgetStatus;
import com.spendsense.budget.dto.BudgetResponse;
import com.spendsense.budget.dto.BudgetSummaryResponse;
import com.spendsense.goal.GoalService;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProactiveInsightServiceTest {

    @Mock private ProactiveInsightRepository proactiveInsightRepository;
    @Mock private FinancialContextService financialContextService;
    @Mock private AnalyticsService analyticsService;
    @Mock private BudgetService budgetService;
    @Mock private GoalService goalService;
    @Mock private AnomalyDetectionService anomalyDetectionService;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;

    private ProactiveInsightDetector detector;
    private ProactiveSignificanceFilter filter;
    private ProactiveInsightService service;
    private User testUser;
    private SpendSenseUserPrincipal principal;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testUser = new User("ada@example.com", "hash", "Ada Lovelace", "Asia/Kolkata");
        principal = new SpendSenseUserPrincipal(testUser);

        when(currentUser.require()).thenReturn(principal);
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));

        detector = new ProactiveInsightDetector(
                financialContextService,
                analyticsService,
                budgetService,
                goalService,
                anomalyDetectionService
        );

        filter = new ProactiveSignificanceFilter(proactiveInsightRepository);

        service = new ProactiveInsightService(
                proactiveInsightRepository,
                detector,
                filter,
                userRepository,
                currentUser,
                objectMapper
        );
    }

    @Nested
    @DisplayName("Category Spike Detection & Filtering")
    class CategorySpikeTests {

        @Test
        void generatesCategorySpikeWhenSpendingExceedsAverageBy25PercentAnd1000Rupees() {
            // Food: average = 8,000, current = 11,200 (+40%, +3,200)
            CategoryProfile foodProfile = new CategoryProfile(
                    TransactionCategory.FOOD,
                    "Food",
                    new BigDecimal("8000.00"),
                    new BigDecimal("11200.00"),
                    new BigDecimal("5000.00"),
                    new BigDecimal("12000.00"),
                    new BigDecimal("35.0"),
                    TrendDirection.INCREASED,
                    "MEDIUM",
                    6,
                    DataSufficiency.HIGH,
                    "Food spending is elevated"
            );

            FinancialProfile profile = new FinancialProfile(
                    LocalDate.now(),
                    6,
                    new DataSufficiencyProfile(DataSufficiency.HIGH, 6, 40, true, true, "OK"),
                    null,
                    null,
                    null,
                    List.of(foodProfile),
                    null,
                    null,
                    List.of(),
                    List.of()
            );

            when(financialContextService.computeFinancialProfile(eq(principal), eq(6))).thenReturn(profile);
            when(proactiveInsightRepository.existsByUser_IdAndDeduplicationKeyAndCreatedAtAfter(any(), any(), any())).thenReturn(false);
            when(proactiveInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<ProactiveInsightDto> insights = service.detectAndGenerateInsights(principal);

            assertThat(insights).isNotEmpty();
            ProactiveInsightDto spike = insights.stream()
                    .filter(i -> i.type() == ProactiveInsightType.CATEGORY_SPIKE)
                    .findFirst()
                    .orElse(null);

            assertThat(spike).isNotNull();
            assertThat(spike.title()).contains("FOOD");
            assertThat(spike.title()).contains("40%");
            assertThat(spike.severity()).isEqualTo(InsightSeverity.WARNING);
            assertThat(spike.supportingFacts().get("category")).isEqualTo("FOOD");
        }

        @Test
        void suppressesCategorySpikeWhenUnderSignificanceThreshold() {
            // Food: average = 8,000, current = 8,300 (+3.75%, only +300 diff)
            CategoryProfile foodProfile = new CategoryProfile(
                    TransactionCategory.FOOD,
                    "Food",
                    new BigDecimal("8000.00"),
                    new BigDecimal("8300.00"),
                    new BigDecimal("7000.00"),
                    new BigDecimal("9000.00"),
                    new BigDecimal("35.0"),
                    TrendDirection.UNCHANGED,
                    "LOW",
                    6,
                    DataSufficiency.HIGH,
                    "Stable food spend"
            );

            FinancialProfile profile = new FinancialProfile(
                    LocalDate.now(),
                    6,
                    new DataSufficiencyProfile(DataSufficiency.HIGH, 6, 40, true, true, "OK"),
                    null,
                    null,
                    null,
                    List.of(foodProfile),
                    null,
                    null,
                    List.of(),
                    List.of()
            );

            when(financialContextService.computeFinancialProfile(eq(principal), eq(6))).thenReturn(profile);

            List<ProactiveInsightCandidate> candidates = detector.detectCandidates(principal);
            assertThat(candidates).noneMatch(c -> c.type() == ProactiveInsightType.CATEGORY_SPIKE);
        }
    }

    @Nested
    @DisplayName("Budget Issues Detection")
    class BudgetDetectionTests {

        @Test
        void generatesCriticalInsightWhenBudgetExceeded() {
            BudgetResponse foodBudget = new BudgetResponse(
                    UUID.randomUUID(),
                    TransactionCategory.FOOD,
                    new BigDecimal("10000.00"),
                    BudgetPeriod.MONTHLY,
                    LocalDate.now().withDayOfMonth(1),
                    LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
                    new BigDecimal("11500.00"),
                    BigDecimal.ZERO,
                    115.0,
                    12,
                    BudgetStatus.EXCEEDED,
                    Instant.now(),
                    Instant.now()
            );

            BudgetSummaryResponse summary = new BudgetSummaryResponse(
                    new BigDecimal("10000.00"),
                    new BigDecimal("11500.00"),
                    BigDecimal.ZERO,
                    115.0,
                    0,
                    0,
                    1,
                    1,
                    List.of(foodBudget)
            );

            when(budgetService.getSummary(any(), any(), any())).thenReturn(summary);
            when(proactiveInsightRepository.existsByUser_IdAndDeduplicationKeyAndCreatedAtAfter(any(), any(), any())).thenReturn(false);
            when(proactiveInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<ProactiveInsightDto> insights = service.detectAndGenerateInsights(principal);

            assertThat(insights).anyMatch(i ->
                    i.type() == ProactiveInsightType.BUDGET_EXCEEDED &&
                    i.severity() == InsightSeverity.CRITICAL &&
                    i.title().contains("FOOD")
            );
        }

        @Test
        void generatesWarningWhenBudgetApproachingLimitAt85Percent() {
            BudgetResponse travelBudget = new BudgetResponse(
                    UUID.randomUUID(),
                    TransactionCategory.TRAVEL,
                    new BigDecimal("10000.00"),
                    BudgetPeriod.MONTHLY,
                    LocalDate.now().withDayOfMonth(1),
                    LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()),
                    new BigDecimal("8800.00"),
                    new BigDecimal("1200.00"),
                    88.0,
                    8,
                    BudgetStatus.WARNING,
                    Instant.now(),
                    Instant.now()
            );

            BudgetSummaryResponse summary = new BudgetSummaryResponse(
                    new BigDecimal("10000.00"),
                    new BigDecimal("8800.00"),
                    new BigDecimal("1200.00"),
                    88.0,
                    0,
                    1,
                    0,
                    1,
                    List.of(travelBudget)
            );

            when(budgetService.getSummary(any(), any(), any())).thenReturn(summary);
            when(proactiveInsightRepository.existsByUser_IdAndDeduplicationKeyAndCreatedAtAfter(any(), any(), any())).thenReturn(false);
            when(proactiveInsightRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<ProactiveInsightDto> insights = service.detectAndGenerateInsights(principal);

            assertThat(insights).anyMatch(i ->
                    i.type() == ProactiveInsightType.BUDGET_WARNING &&
                    i.severity() == InsightSeverity.WARNING &&
                    i.title().contains("TRAVEL")
            );
        }
    }

    @Nested
    @DisplayName("Deduplication & Suppression Tests")
    class SuppressionTests {

        @Test
        void suppressesInsightIfAlreadyCreatedWithinCooldown() {
            ProactiveInsightCandidate candidate = new ProactiveInsightCandidate(
                    ProactiveInsightType.CATEGORY_SPIKE,
                    InsightSeverity.WARNING,
                    "Food spike",
                    "Summary",
                    Map.of("category", "FOOD", "percentageChange", 35.0),
                    "Recommendation",
                    "CATEGORY_SPIKE:FOOD:2026-09",
                    80.0
            );

            when(proactiveInsightRepository.existsByUser_IdAndDeduplicationKeyAndCreatedAtAfter(
                    eq(testUser.getId()), eq("CATEGORY_SPIKE:FOOD:2026-09"), any()
            )).thenReturn(true);

            List<ProactiveInsightCandidate> filtered = filter.filterAndRank(testUser.getId(), List.of(candidate));
            assertThat(filtered).isEmpty();
        }

        @Test
        void limitsSurfacedInsightsToTopRankedCandidates() {
            List<ProactiveInsightCandidate> candidates = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                candidates.add(new ProactiveInsightCandidate(
                        ProactiveInsightType.CATEGORY_SPIKE,
                        InsightSeverity.WARNING,
                        "Title " + i,
                        "Summary " + i,
                        Map.of("percentageChange", 25.0 + i),
                        "Recommendation",
                        "KEY_" + i,
                        50.0 + i * 10
                ));
            }

            when(proactiveInsightRepository.existsByUser_IdAndDeduplicationKeyAndCreatedAtAfter(any(), any(), any())).thenReturn(false);

            List<ProactiveInsightCandidate> filtered = filter.filterAndRank(testUser.getId(), candidates);
            assertThat(filtered).hasSize(3);
            assertThat(filtered.get(0).deduplicationKey()).isEqualTo("KEY_6"); // Highest score (110.0)
            assertThat(filtered.get(1).deduplicationKey()).isEqualTo("KEY_5"); // Second highest (100.0)
            assertThat(filtered.get(2).deduplicationKey()).isEqualTo("KEY_4"); // Third highest (90.0)
        }
    }

    @Test
    void successfullyDismissesInsight() {
        UUID insightId = UUID.randomUUID();
        when(proactiveInsightRepository.dismissInsight(eq(insightId), eq(testUser.getId()))).thenReturn(1);

        service.dismissInsight(insightId);
        verify(proactiveInsightRepository).dismissInsight(eq(insightId), eq(testUser.getId()));
    }
}
