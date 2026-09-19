package com.spendsense.categorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.model.BaselineCategorizationModel;
import com.spendsense.categorization.model.CategorizationCandidate;
import com.spendsense.categorization.model.CategorizationInput;
import com.spendsense.categorization.model.CategorizationModel;
import com.spendsense.categorization.model.CategorizationPrediction;
import com.spendsense.categorization.model.ModelHealth;
import com.spendsense.categorization.model.ModelInferenceException;
import com.spendsense.categorization.model.ModelMetadata;
import com.spendsense.categorization.policy.CategorizationConfidencePolicy;
import com.spendsense.categorization.rule.AtmCashWithdrawalRule;
import com.spendsense.categorization.rule.BankInterestAndDividendRule;
import com.spendsense.categorization.rule.InternalTransferRule;
import com.spendsense.categorization.rule.PayrollAndIncomeRule;
import com.spendsense.categorization.rule.RuleEngine;
import com.spendsense.categorization.rule.TaxAndGovernmentRule;
import com.spendsense.transaction.PaymentMethod;
import com.spendsense.transaction.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Phase 6G: Tiered Categorization Orchestration Tests")
class TieredCategorizationOrchestrationTest {

    private TransactionNormalizer normalizer;
    private RuleEngine ruleEngine;
    private CategorizationConfidencePolicy confidencePolicy;

    @Mock
    private UserCategorizationMemoryService userMemoryService;

    @Mock
    private CategorizationModel baselineModel;

    @Mock
    private CategorizationModel semanticModel;

    private CategorizationService tieredService;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
        ruleEngine = new RuleEngine(List.of(
                new PayrollAndIncomeRule(),
                new TaxAndGovernmentRule(),
                new BankInterestAndDividendRule(),
                new AtmCashWithdrawalRule(),
                new InternalTransferRule()
        ));
        confidencePolicy = new CategorizationConfidencePolicy(0.85, 0.60);

        when(baselineModel.getModelHealth()).thenReturn(ModelHealth.healthy(
                new ModelMetadata("v1.0.0-baseline-tfidf-lr", "ds", "pre", "algo", "time", List.of())
        ));
        when(semanticModel.getModelHealth()).thenReturn(ModelHealth.healthy(
                new ModelMetadata("v1.0.0-semantic-minilm-l6", "ds", "pre", "algo", "time", List.of())
        ));

        tieredService = new CategorizationService(
                normalizer,
                ruleEngine,
                userMemoryService,
                baselineModel,
                semanticModel,
                confidencePolicy,
                true, // mlEnabled
                true, // semanticEnabled
                0.60  // escalationThreshold
        );
    }

    @Nested
    @DisplayName("Precedence Hierarchy & Tiered Escalation")
    class PrecedenceTests {

        @Test
        @DisplayName("Deterministic rule wins -> Semantic model and baseline are NEVER invoked")
        void testDeterministicRuleWins() {
            CategorizationRequest req = new CategorizationRequest(
                    UUID.randomUUID(),
                    "STATE FARM PAYROLL DIRECT DEP",
                    "Direct deposit salary",
                    new BigDecimal("3500.00"),
                    "BANK_TRANSFER",
                    LocalDate.now()
            );

            CategorizationResult result = tieredService.categorize(req);

            assertThat(result.source()).isEqualTo(CategorizationSource.RULE);
            assertThat(result.category()).isEqualTo(TransactionCategory.OTHER); // Payroll
            verify(baselineModel, never()).predict(any());
            verify(semanticModel, never()).predict(any());
        }

        @Test
        @DisplayName("Strong user memory wins -> Semantic model is NEVER invoked and cannot override")
        void testStrongUserMemoryWins() {
            UUID userId = UUID.randomUUID();
            String merchant = "starbucks";
            when(userMemoryService.getPreference(eq(userId), eq("starbucks"))).thenReturn(
                    Optional.of(new UserMemoryPreference(TransactionCategory.FOOD, "starbucks", 1.0, ConfidenceLevel.HIGH, 5, 5, false, true, java.time.Instant.now()))
            );

            CategorizationRequest req = new CategorizationRequest(
                    userId,
                    merchant,
                    "morning coffee",
                    new BigDecimal("6.50"),
                    "DEBIT_CARD",
                    LocalDate.now()
            );

            CategorizationResult result = tieredService.categorize(req);

            assertThat(result.source()).isEqualTo(CategorizationSource.USER_MEMORY);
            assertThat(result.category()).isEqualTo(TransactionCategory.FOOD);
            assertThat(result.personalizationApplied()).isTrue();
            verify(baselineModel, never()).predict(any());
            verify(semanticModel, never()).predict(any());
        }

        @Test
        @DisplayName("Baseline high confidence (>= 0.85) -> Semantic model is NOT invoked")
        void testBaselineHighConfidenceBypassesSemantic() {
            UUID userId = UUID.randomUUID();
            CategorizationPrediction highPred = new CategorizationPrediction(
                    TransactionCategory.ENTERTAINMENT,
                    0.92,
                    List.of(new CategorizationCandidate(TransactionCategory.ENTERTAINMENT, 0.92)),
                    "v1.0.0-baseline-tfidf-lr"
            );
            when(baselineModel.predict(any())).thenReturn(highPred);

            CategorizationRequest req = new CategorizationRequest(
                    userId,
                    "netflix streaming",
                    "monthly subscription",
                    new BigDecimal("15.99"),
                    "CREDIT_CARD",
                    LocalDate.now()
            );

            CategorizationResult result = tieredService.categorize(req);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.category()).isEqualTo(TransactionCategory.ENTERTAINMENT);
            assertThat(result.confidence()).isEqualTo(0.92);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(result.modelVersion()).isEqualTo("v1.0.0-baseline-tfidf-lr");

            verify(baselineModel).predict(any());
            verify(semanticModel, never()).predict(any());
        }

        @Test
        @DisplayName("Baseline medium confidence (0.60 - 0.85) -> Accepted without escalation")
        void testBaselineMediumConfidenceNoEscalation() {
            UUID userId = UUID.randomUUID();
            CategorizationPrediction medPred = new CategorizationPrediction(
                    TransactionCategory.SHOPPING,
                    0.72,
                    List.of(new CategorizationCandidate(TransactionCategory.SHOPPING, 0.72)),
                    "v1.0.0-baseline-tfidf-lr"
            );
            when(baselineModel.predict(any())).thenReturn(medPred);

            CategorizationRequest req = new CategorizationRequest(
                    userId,
                    "walmart mart",
                    "household goods",
                    new BigDecimal("45.00"),
                    "CREDIT_CARD",
                    LocalDate.now()
            );

            CategorizationResult result = tieredService.categorize(req);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.category()).isEqualTo(TransactionCategory.SHOPPING);
            assertThat(result.confidence()).isEqualTo(0.72);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
            assertThat(result.requiresUserConfirmation()).isTrue();

            verify(baselineModel).predict(any());
            verify(semanticModel, never()).predict(any());
        }

        @Test
        @DisplayName("Baseline low confidence (< 0.60) -> Escalates to semantic model")
        void testBaselineLowConfidenceEscalatesToSemantic() {
            UUID userId = UUID.randomUUID();
            CategorizationPrediction lowBaseline = new CategorizationPrediction(
                    TransactionCategory.OTHER,
                    0.42,
                    List.of(new CategorizationCandidate(TransactionCategory.OTHER, 0.42)),
                    "v1.0.0-baseline-tfidf-lr"
            );
            when(baselineModel.predict(any())).thenReturn(lowBaseline);

            CategorizationPrediction highSemantic = new CategorizationPrediction(
                    TransactionCategory.EDUCATION,
                    0.96,
                    List.of(new CategorizationCandidate(TransactionCategory.EDUCATION, 0.96)),
                    "v1.0.0-semantic-minilm-l6"
            );
            when(semanticModel.predict(any())).thenReturn(highSemantic);

            CategorizationRequest req = new CategorizationRequest(
                    userId,
                    "Udemy",
                    "semester fee via Udemy",
                    new BigDecimal("120.00"),
                    "CREDIT_CARD",
                    LocalDate.now()
            );

            CategorizationResult result = tieredService.categorize(req);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.category()).isEqualTo(TransactionCategory.EDUCATION);
            assertThat(result.confidence()).isEqualTo(0.96);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(result.modelVersion()).isEqualTo("v1.0.0-semantic-minilm-l6");
            assertThat(result.rationale()).contains("Semantic escalation applied");

            verify(baselineModel).predict(any());
            verify(semanticModel).predict(any());
        }

        @Test
        @DisplayName("Semantic model failure falls back safely to baseline or fallback without crashing")
        void testSemanticFailureFallsBackSafely() {
            UUID userId = UUID.randomUUID();
            CategorizationPrediction lowBaseline = new CategorizationPrediction(
                    TransactionCategory.SHOPPING,
                    0.52,
                    List.of(new CategorizationCandidate(TransactionCategory.SHOPPING, 0.52)),
                    "v1.0.0-baseline-tfidf-lr"
            );
            when(baselineModel.predict(any())).thenReturn(lowBaseline);
            when(semanticModel.predict(any())).thenThrow(new ModelInferenceException("ONNX runtime error"));

            CategorizationRequest req = new CategorizationRequest(
                    userId,
                    "mysterious boutique",
                    "apparel",
                    new BigDecimal("75.00"),
                    "CREDIT_CARD",
                    LocalDate.now()
            );

            CategorizationResult result = tieredService.categorize(req);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.category()).isEqualTo(TransactionCategory.SHOPPING);
            assertThat(result.confidence()).isEqualTo(0.52);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.LOW);
            assertThat(result.requiresUserConfirmation()).isTrue();
        }

        @Test
        @DisplayName("Disabled semantic model leaves baseline intact without escalation")
        void testDisabledSemanticModel() {
            CategorizationService serviceNoSemantic = new CategorizationService(
                    normalizer,
                    ruleEngine,
                    userMemoryService,
                    baselineModel,
                    semanticModel,
                    confidencePolicy,
                    true,  // mlEnabled
                    false, // semanticEnabled = false
                    0.60
            );

            CategorizationPrediction lowBaseline = new CategorizationPrediction(
                    TransactionCategory.FOOD,
                    0.45,
                    List.of(new CategorizationCandidate(TransactionCategory.FOOD, 0.45)),
                    "v1.0.0-baseline-tfidf-lr"
            );
            when(baselineModel.predict(any())).thenReturn(lowBaseline);

            CategorizationRequest req = new CategorizationRequest(
                    UUID.randomUUID(),
                    "unknown cafe",
                    "coffee",
                    new BigDecimal("5.00"),
                    "DEBIT_CARD",
                    LocalDate.now()
            );

            CategorizationResult result = serviceNoSemantic.categorize(req);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.modelVersion()).isEqualTo("v1.0.0-baseline-tfidf-lr");
            verify(semanticModel, never()).predict(any());
        }
    }

    @Nested
    @DisplayName("Confidence Threshold Boundary Conditions")
    class ConfidenceThresholdTests {

        @Test
        @DisplayName("Exactly 0.85 threshold evaluates to HIGH")
        void testBoundaryHigh() {
            when(baselineModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.FOOD, 0.85, List.of(), "v1"
            ));

            CategorizationResult res = tieredService.categorize(new CategorizationRequest(
                    UUID.randomUUID(), "rest", "dining", new BigDecimal("30"), "CREDIT_CARD", LocalDate.now()
            ));

            assertThat(res.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(res.requiresUserConfirmation()).isFalse();
            verify(semanticModel, never()).predict(any());
        }

        @Test
        @DisplayName("Exactly 0.60 threshold evaluates to MEDIUM and does NOT trigger escalation")
        void testBoundaryMedium() {
            when(baselineModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.FOOD, 0.60, List.of(), "v1"
            ));

            CategorizationResult res = tieredService.categorize(new CategorizationRequest(
                    UUID.randomUUID(), "rest", "dining", new BigDecimal("30"), "CREDIT_CARD", LocalDate.now()
            ));

            assertThat(res.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
            assertThat(res.requiresUserConfirmation()).isTrue();
            verify(semanticModel, never()).predict(any());
        }

        @Test
        @DisplayName("Just below 0.60 (0.5999) triggers semantic escalation")
        void testBoundaryBelowMediumTriggersEscalation() {
            when(baselineModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.FOOD, 0.5999, List.of(), "v1"
            ));
            when(semanticModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.FOOD, 0.91, List.of(), "v1.0.0-semantic-minilm-l6"
            ));

            CategorizationResult res = tieredService.categorize(new CategorizationRequest(
                    UUID.randomUUID(), "rest", "dining", new BigDecimal("30"), "CREDIT_CARD", LocalDate.now()
            ));

            assertThat(res.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(res.modelVersion()).isEqualTo("v1.0.0-semantic-minilm-l6");
            verify(semanticModel).predict(any());
        }
    }

    @Nested
    @DisplayName("User Isolation & Privacy Tests")
    class UserIsolationTests {

        @Test
        @DisplayName("User A memory never influences User B categorization")
        void testUserMemoryIsolation() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            String merchant = "acme shop";

            // User A has strong memory for SHOPPING
            when(userMemoryService.getPreference(eq(userA), eq("acme shop"))).thenReturn(
                    Optional.of(new UserMemoryPreference(TransactionCategory.SHOPPING, "acme shop", 1.0, ConfidenceLevel.HIGH, 5, 5, false, true, java.time.Instant.now()))
            );
            // User B has NO memory
            when(userMemoryService.getPreference(eq(userB), eq("acme shop"))).thenReturn(Optional.empty());

            when(baselineModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.OTHER, 0.50, List.of(), "v1"
            ));
            when(semanticModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.PERSONAL, 0.90, List.of(), "v1.0.0-semantic-minilm-l6"
            ));

            // User A transaction
            CategorizationResult resA = tieredService.categorize(new CategorizationRequest(
                    userA, merchant, "tools", new BigDecimal("20"), "CREDIT_CARD", LocalDate.now()
            ));
            assertThat(resA.source()).isEqualTo(CategorizationSource.USER_MEMORY);
            assertThat(resA.category()).isEqualTo(TransactionCategory.SHOPPING);

            // User B transaction receives global ML prediction, unaffected by User A
            CategorizationResult resB = tieredService.categorize(new CategorizationRequest(
                    userB, merchant, "tools", new BigDecimal("20"), "CREDIT_CARD", LocalDate.now()
            ));
            assertThat(resB.source()).isEqualTo(CategorizationSource.ML);
            assertThat(resB.category()).isEqualTo(TransactionCategory.PERSONAL);
            assertThat(resB.modelVersion()).isEqualTo("v1.0.0-semantic-minilm-l6");
        }
    }
}
