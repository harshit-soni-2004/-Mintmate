package com.spendsense.categorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.model.BaselineCategorizationModel;
import com.spendsense.categorization.model.CategorizationCandidate;
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

import java.time.Instant;
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
@DisplayName("Categorization Engine Orchestration Tests")
class CategorizationServiceTest {

    private TransactionNormalizer normalizer;
    private RuleEngine ruleEngine;
    private CategorizationConfidencePolicy confidencePolicy;

    @Mock
    private UserCategorizationMemoryService userMemoryService;

    @Mock
    private CategorizationModel mockMlModel;

    private CategorizationModel realBaselineModel;

    private CategorizationService orchestratorWithRealModel;
    private CategorizationService orchestratorWithMockModel;
    private CategorizationService orchestratorWithDisabledMl;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
        confidencePolicy = new CategorizationConfidencePolicy(0.85, 0.60);

        ruleEngine = new RuleEngine(List.of(
                new PayrollAndIncomeRule(),
                new TaxAndGovernmentRule(),
                new BankInterestAndDividendRule(),
                new AtmCashWithdrawalRule(),
                new InternalTransferRule()
        ));

        realBaselineModel = new BaselineCategorizationModel(
                new ClassPathResource("model/baseline_model_v1.json"),
                new ObjectMapper()
        );

        orchestratorWithRealModel = new CategorizationService(
                normalizer,
                ruleEngine,
                userMemoryService,
                realBaselineModel,
                confidencePolicy,
                true
        );

        orchestratorWithMockModel = new CategorizationService(
                normalizer,
                ruleEngine,
                userMemoryService,
                mockMlModel,
                confidencePolicy,
                true
        );

        orchestratorWithDisabledMl = new CategorizationService(
                normalizer,
                ruleEngine,
                userMemoryService,
                mockMlModel,
                confidencePolicy,
                false
        );
    }

    @Nested
    @DisplayName("Stage 1: Deterministic Rules Precedence")
    class RulePrecedenceTests {

        @Test
        @DisplayName("Should categorize payroll deposits via RULE without calling memory or ML")
        void shouldTriggerPayrollRule() {
            CategorizationRequest request = CategorizationRequest.of(
                    UUID.randomUUID(),
                    "ACME CORP",
                    "PAYROLL DIRECT DEP 09/15"
            );

            CategorizationResult result = orchestratorWithRealModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.RULE);
            assertThat(result.category()).isEqualTo(TransactionCategory.OTHER);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(result.confidence()).isEqualTo(1.0);
            assertThat(result.personalizationApplied()).isFalse();
            assertThat(result.requiresUserConfirmation()).isFalse();
            assertThat(result.rationale()).contains("payroll/salary credit rule");

            verify(userMemoryService, never()).getPreference(any(), any());
        }

        @Test
        @DisplayName("Should categorize tax payments via RULE to BILLS")
        void shouldTriggerTaxRule() {
            CategorizationRequest request = CategorizationRequest.of(
                    UUID.randomUUID(),
                    "IRS USATAX",
                    "ELECTRONIC PAYMENT 1040"
            );

            CategorizationResult result = orchestratorWithRealModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.RULE);
            assertThat(result.category()).isEqualTo(TransactionCategory.BILLS);
            assertThat(result.requiresUserConfirmation()).isFalse();
        }
    }

    @Nested
    @DisplayName("Stage 2: Strong User Memory Precedence")
    class StrongUserMemoryTests {

        @Test
        @DisplayName("Should prioritize strong user memory over global ML model")
        void shouldPrioritizeStrongMemory() {
            UUID userId = UUID.randomUUID();
            // Normalizer will resolve "AMAZON*ORDER*123" -> "amazon"
            UserMemoryPreference strongPref = new UserMemoryPreference(
                    TransactionCategory.PERSONAL,
                    "amazon",
                    0.95,
                    ConfidenceLevel.HIGH,
                    5,
                    5,
                    false,
                    true,
                    Instant.now()
            );

            when(userMemoryService.getPreference(eq(userId), eq("amazon")))
                    .thenReturn(Optional.of(strongPref));

            CategorizationRequest request = CategorizationRequest.of(
                    userId,
                    "AMAZON*ORDER*123",
                    "ORDER REF 8872"
            );

            CategorizationResult result = orchestratorWithRealModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.USER_MEMORY);
            assertThat(result.category()).isEqualTo(TransactionCategory.PERSONAL);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(result.confidence()).isEqualTo(0.95);
            assertThat(result.personalizationApplied()).isTrue();
            assertThat(result.requiresUserConfirmation()).isFalse();
            assertThat(result.rationale()).contains("Strong user-specific memory");
        }
    }

    @Nested
    @DisplayName("Stage 3: Weak / Tentative & Conflicting Memory Reconciliation")
    class MemoryReconciliationTests {

        @Test
        @DisplayName("Weak/tentative user memory must NOT override high-confidence ML prediction")
        void weakMemoryMustNotOverrideHighConfidenceMl() {
            UUID userId = UUID.randomUUID();
            // Single observation = tentative memory (0.60 strength score)
            UserMemoryPreference tentativePref = new UserMemoryPreference(
                    TransactionCategory.PERSONAL,
                    "amazon",
                    0.60,
                    ConfidenceLevel.MEDIUM,
                    1,
                    1,
                    false,
                    false, // tentative, not strong!
                    Instant.now()
            );

            when(userMemoryService.getPreference(eq(userId), eq("amazon")))
                    .thenReturn(Optional.of(tentativePref));

            when(mockMlModel.getModelHealth()).thenReturn(ModelHealth.healthy(ModelMetadata.unknown()));
            when(mockMlModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.SHOPPING,
                    0.94, // HIGH confidence ML prediction
                    List.of(
                            new CategorizationCandidate(TransactionCategory.SHOPPING, 0.94),
                            new CategorizationCandidate(TransactionCategory.PERSONAL, 0.03)
                    ),
                    "mock-v1"
            ));

            CategorizationRequest request = CategorizationRequest.of(userId, "amazon", "online order");
            CategorizationResult result = orchestratorWithMockModel.categorize(request);

            // ML predicts SHOPPING with high confidence (0.94 >= 0.85)
            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.category()).isEqualTo(TransactionCategory.SHOPPING);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            // Because ML differed from tentative user memory, user confirmation is requested
            assertThat(result.requiresUserConfirmation()).isTrue();
            assertThat(result.personalizationApplied()).isTrue();
            assertThat(result.rationale()).contains("ML high confidence");
        }

        @Test
        @DisplayName("Weak user memory wins when ML model has uncertain/medium confidence")
        void weakMemoryWinsOverUncertainMl() {
            UUID userId = UUID.randomUUID();
            UserMemoryPreference tentativePref = new UserMemoryPreference(
                    TransactionCategory.FOOD,
                    "corner cafe",
                    0.60,
                    ConfidenceLevel.MEDIUM,
                    1,
                    1,
                    false,
                    false,
                    Instant.now()
            );

            when(userMemoryService.getPreference(eq(userId), eq("corner cafe")))
                    .thenReturn(Optional.of(tentativePref));

            when(mockMlModel.getModelHealth()).thenReturn(ModelHealth.healthy(ModelMetadata.unknown()));
            when(mockMlModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.OTHER,
                    0.52, // LOW confidence ML
                    List.of(new CategorizationCandidate(TransactionCategory.OTHER, 0.52)),
                    "mock-v1"
            ));

            CategorizationRequest request = CategorizationRequest.of(userId, "corner cafe", null);
            CategorizationResult result = orchestratorWithMockModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.USER_MEMORY);
            assertThat(result.category()).isEqualTo(TransactionCategory.FOOD);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
            assertThat(result.requiresUserConfirmation()).isTrue();
            assertThat(result.rationale()).contains("Tentative user preference prioritized over uncertain ML");
        }

        @Test
        @DisplayName("Conflicting user history defers to ML with confirmation required")
        void conflictingHistoryDefersToMl() {
            UUID userId = UUID.randomUUID();
            UserMemoryPreference conflictingPref = new UserMemoryPreference(
                    TransactionCategory.SHOPPING,
                    "walmart",
                    0.50,
                    ConfidenceLevel.LOW,
                    4,
                    2,
                    true, // conflicting history!
                    false,
                    Instant.now()
            );

            when(userMemoryService.getPreference(eq(userId), eq("walmart")))
                    .thenReturn(Optional.of(conflictingPref));

            CategorizationRequest request = CategorizationRequest.of(userId, "walmart", "store purchase");
            CategorizationResult result = orchestratorWithRealModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.category()).isEqualTo(TransactionCategory.SHOPPING);
            assertThat(result.requiresUserConfirmation()).isTrue();
            assertThat(result.rationale()).contains("conflicting user history");
        }
    }

    @Nested
    @DisplayName("Stage 4: Cold Start & Confidence Tiers")
    class ColdStartAndConfidenceTests {

        @Test
        @DisplayName("Cold start with high confidence ML qualifies for auto-assignment")
        void coldStartHighConfidence() {
            CategorizationRequest request = CategorizationRequest.of("STARBUCKS", "COFFEE SEATTLE WA");
            CategorizationResult result = orchestratorWithRealModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.category()).isEqualTo(TransactionCategory.FOOD);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(result.confidence()).isGreaterThanOrEqualTo(0.85);
            assertThat(result.requiresUserConfirmation()).isFalse();
            assertThat(result.personalizationApplied()).isFalse();
        }

        @Test
        @DisplayName("Medium confidence ML prediction requires user confirmation")
        void coldStartMediumConfidence() {
            when(mockMlModel.getModelHealth()).thenReturn(ModelHealth.healthy(ModelMetadata.unknown()));
            when(mockMlModel.predict(any())).thenReturn(new CategorizationPrediction(
                    TransactionCategory.ENTERTAINMENT,
                    0.72,
                    List.of(new CategorizationCandidate(TransactionCategory.ENTERTAINMENT, 0.72)),
                    "mock-v1"
            ));

            CategorizationRequest request = CategorizationRequest.of("AMBIGUOUS STORE", "STORE PURCHASE");
            CategorizationResult result = orchestratorWithMockModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
            assertThat(result.requiresUserConfirmation()).isTrue();
            assertThat(result.rationale()).contains("confirmation recommended");
        }

        @Test
        @DisplayName("Low confidence ML prediction flags manual verification (Indian/UPI limitation)")
        void coldStartLowConfidenceIndianUpi() {
            // "paytm billpay electricity" triggers low baseline confidence (~0.33)
            CategorizationRequest request = CategorizationRequest.of("PAYTM", "BILLPAY ELECTRICITY");
            CategorizationResult result = orchestratorWithRealModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.ML);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.LOW);
            assertThat(result.confidence()).isLessThan(0.60);
            assertThat(result.requiresUserConfirmation()).isTrue();
            assertThat(result.rationale()).contains("manual verification required");
        }
    }

    @Nested
    @DisplayName("Stage 5: Resilience & Fallbacks")
    class ResilienceAndFallbackTests {

        @Test
        @DisplayName("Should gracefully fall back to OTHER when ML model is unavailable")
        void fallbackWhenModelUnavailable() {
            when(mockMlModel.getModelHealth()).thenReturn(ModelHealth.unavailable("Artifact missing"));

            CategorizationRequest request = CategorizationRequest.of("UNKNOWN MERCHANT", "XYZ 123");
            CategorizationResult result = orchestratorWithMockModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.FALLBACK);
            assertThat(result.category()).isEqualTo(TransactionCategory.OTHER);
            assertThat(result.confidenceLevel()).isEqualTo(ConfidenceLevel.NONE);
            assertThat(result.requiresUserConfirmation()).isTrue();
            verify(mockMlModel, never()).predict(any());
        }

        @Test
        @DisplayName("Should gracefully fall back to OTHER when ML model throws inference exception")
        void fallbackWhenModelThrowsException() {
            when(mockMlModel.getModelHealth()).thenReturn(ModelHealth.healthy(ModelMetadata.unknown()));
            when(mockMlModel.predict(any())).thenThrow(new ModelInferenceException("Internal scoring error"));

            CategorizationRequest request = CategorizationRequest.of("CRASH MERCHANT", "CRASH");
            CategorizationResult result = orchestratorWithMockModel.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.FALLBACK);
            assertThat(result.category()).isEqualTo(TransactionCategory.OTHER);
            assertThat(result.requiresUserConfirmation()).isTrue();
        }

        @Test
        @DisplayName("Should operate without ML invocation when ML is disabled in config")
        void operateWhenMlDisabled() {
            CategorizationRequest request = CategorizationRequest.of("ANY MERCHANT", "ANY DESC");
            CategorizationResult result = orchestratorWithDisabledMl.categorize(request);

            assertThat(result.source()).isEqualTo(CategorizationSource.FALLBACK);
            assertThat(result.category()).isEqualTo(TransactionCategory.OTHER);
            verify(mockMlModel, never()).predict(any());
        }
    }

    @Nested
    @DisplayName("Stage 6: User Isolation & Normalization Invariant")
    class UserIsolationAndNormalizationTests {

        @Test
        @DisplayName("User A memory must not bleed or affect User B")
        void userIsolationInvariant() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();

            UserMemoryPreference userAPref = new UserMemoryPreference(
                    TransactionCategory.PERSONAL,
                    "amazon",
                    0.95,
                    ConfidenceLevel.HIGH,
                    3,
                    3,
                    false,
                    true,
                    Instant.now()
            );

            when(userMemoryService.getPreference(eq(userA), eq("amazon")))
                    .thenReturn(Optional.of(userAPref));
            when(userMemoryService.getPreference(eq(userB), eq("amazon")))
                    .thenReturn(Optional.empty());

            // User A categorizes Amazon
            CategorizationResult resultA = orchestratorWithRealModel.categorize(
                    CategorizationRequest.of(userA, "AMAZON", "ORDER")
            );
            assertThat(resultA.source()).isEqualTo(CategorizationSource.USER_MEMORY);
            assertThat(resultA.category()).isEqualTo(TransactionCategory.PERSONAL);

            // User B categorizes Amazon
            CategorizationResult resultB = orchestratorWithRealModel.categorize(
                    CategorizationRequest.of(userB, "AMAZON", "ORDER")
            );
            assertThat(resultB.source()).isEqualTo(CategorizationSource.ML);
            assertThat(resultB.category()).isEqualTo(TransactionCategory.SHOPPING);
        }

        @Test
        @DisplayName("Messy merchant strings should normalize and resolve to user memory")
        void messyMerchantNormalizationMatchesMemory() {
            UUID userId = UUID.randomUUID();
            UserMemoryPreference swiggyPref = new UserMemoryPreference(
                    TransactionCategory.FOOD,
                    "swiggy",
                    0.95,
                    ConfidenceLevel.HIGH,
                    4,
                    4,
                    false,
                    true,
                    Instant.now()
            );

            when(userMemoryService.getPreference(eq(userId), eq("swiggy")))
                    .thenReturn(Optional.of(swiggyPref));

            List<String> variants = List.of(
                    "SWIGGY",
                    "SWIGGY*ORDER*123",
                    "UPI-SWIGGY-834723",
                    "SWIGGY IN"
            );

            for (String variant : variants) {
                CategorizationResult result = orchestratorWithRealModel.categorize(
                        CategorizationRequest.of(userId, variant, "food delivery")
                );
                assertThat(result.source())
                        .as("Variant '%s' must resolve to USER_MEMORY", variant)
                        .isEqualTo(CategorizationSource.USER_MEMORY);
                assertThat(result.category()).isEqualTo(TransactionCategory.FOOD);
                assertThat(result.normalizedMerchant()).isEqualTo("swiggy");
            }
        }
    }

    @Nested
    @DisplayName("Stage 7: Mandatory Personalization Lifecycle Flow")
    class PersonalizationLifecycleFlowTest {

        @Test
        @DisplayName("Simulate complete User A learning flow: Cold start ML -> User feedback -> Strong memory -> User B unaffected")
        void completePersonalizationLifecycle() {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();

            // Step 1: User A has NO memory yet (Cold start)
            when(userMemoryService.getPreference(eq(userA), eq("amazon")))
                    .thenReturn(Optional.empty());

            CategorizationResult step1 = orchestratorWithRealModel.categorize(
                    CategorizationRequest.of(userA, "Amazon", "Online purchase")
            );
            // ML predicts SHOPPING
            assertThat(step1.source()).isEqualTo(CategorizationSource.ML);
            assertThat(step1.category()).isEqualTo(TransactionCategory.SHOPPING);

            // Step 2: User A selects PERSONAL (1st feedback)
            // Memory is now tentative (1 observation, 0.60 strength)
            UserMemoryPreference tentativePref = new UserMemoryPreference(
                    TransactionCategory.PERSONAL,
                    "amazon",
                    0.60,
                    ConfidenceLevel.MEDIUM,
                    1,
                    1,
                    false,
                    false,
                    Instant.now()
            );
            when(userMemoryService.getPreference(eq(userA), eq("amazon")))
                    .thenReturn(Optional.of(tentativePref));

            // Step 3: Second transaction for User A
            // ML confidence for "amazon online order" is 0.77 (MEDIUM).
            // Tentative user preference wins over uncertain ML prediction!
            CategorizationResult step3 = orchestratorWithRealModel.categorize(
                    CategorizationRequest.of(userA, "AMAZON*ORDER*123", "Online order")
            );
            assertThat(step3.source()).isEqualTo(CategorizationSource.USER_MEMORY);
            assertThat(step3.category()).isEqualTo(TransactionCategory.PERSONAL);
            assertThat(step3.requiresUserConfirmation()).isTrue();

            // Step 4: User A reinforces PERSONAL again (2nd feedback -> 2/2 observations, strong!)
            UserMemoryPreference strongPref = new UserMemoryPreference(
                    TransactionCategory.PERSONAL,
                    "amazon",
                    0.85,
                    ConfidenceLevel.HIGH,
                    2,
                    2,
                    false,
                    true, // Now strong!
                    Instant.now()
            );
            when(userMemoryService.getPreference(eq(userA), eq("amazon")))
                    .thenReturn(Optional.of(strongPref));

            // Step 5: Third transaction for User A
            // Now strong user memory takes precedence with zero user confirmation required!
            CategorizationResult step5 = orchestratorWithRealModel.categorize(
                    CategorizationRequest.of(userA, "Amazon", "Subscription")
            );
            assertThat(step5.source()).isEqualTo(CategorizationSource.USER_MEMORY);
            assertThat(step5.category()).isEqualTo(TransactionCategory.PERSONAL);
            assertThat(step5.requiresUserConfirmation()).isFalse();
            assertThat(step5.personalizationApplied()).isTrue();

            // Step 6: User B categorizes Amazon
            when(userMemoryService.getPreference(eq(userB), eq("amazon")))
                    .thenReturn(Optional.empty());

            CategorizationResult step6 = orchestratorWithRealModel.categorize(
                    CategorizationRequest.of(userB, "Amazon", "Online purchase")
            );
            // User B is 100% unaffected by User A's personalization!
            assertThat(step6.source()).isEqualTo(CategorizationSource.ML);
            assertThat(step6.category()).isEqualTo(TransactionCategory.SHOPPING);
        }
    }
}
