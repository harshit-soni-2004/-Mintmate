package com.spendsense.transaction;

import com.spendsense.categorization.CategorizationFeedbackRepository;
import com.spendsense.categorization.CategorizationFeedbackService;
import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.categorization.TransactionNormalizer;
import com.spendsense.categorization.UserCategorizationMemory;
import com.spendsense.categorization.UserCategorizationMemoryRepository;
import com.spendsense.categorization.UserCategorizationMemoryService;
import com.spendsense.categorization.model.BaselineCategorizationModel;
import com.spendsense.categorization.policy.CategorizationConfidencePolicy;
import com.spendsense.categorization.rule.AtmCashWithdrawalRule;
import com.spendsense.categorization.rule.BankInterestAndDividendRule;
import com.spendsense.categorization.rule.InternalTransferRule;
import com.spendsense.categorization.rule.PayrollAndIncomeRule;
import com.spendsense.categorization.rule.RuleEngine;
import com.spendsense.categorization.rule.TaxAndGovernmentRule;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.dto.TransactionRequest;
import com.spendsense.transaction.dto.TransactionResponse;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Phase 6H: End-to-End Transaction Categorization & Learning Loop Tests")
class TransactionCategorizationIntegrationTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;
    @Mock private CategorizationFeedbackRepository feedbackRepository;
    @Mock private UserCategorizationMemoryRepository memoryRepository;

    private User userAda;
    private User userBob;
    private TransactionService transactionService;
    private UserCategorizationMemoryService userMemoryService;
    private CategorizationFeedbackService feedbackService;
    private CategorizationService categorizationService;

    private final List<UserCategorizationMemory> inMemoryAdaMemories = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        userAda = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        userBob = new User("bob@example.com", "hash", "Bob", "Asia/Kolkata");

        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(userAda));
        when(userRepository.findById(userAda.getId())).thenReturn(Optional.of(userAda));
        when(userRepository.findById(userBob.getId())).thenReturn(Optional.of(userBob));

        TransactionNormalizer normalizer = new TransactionNormalizer();

        // Memory repository backing store for learning loop
        when(memoryRepository.findAllByUserIdAndNormalizedMerchant(eq(userAda.getId()), any()))
                .thenAnswer(inv -> {
                    String merch = inv.getArgument(1);
                    return inMemoryAdaMemories.stream()
                            .filter(m -> m.getNormalizedMerchant().equals(merch))
                            .toList();
                });
        when(memoryRepository.findByUserIdAndNormalizedMerchantAndCategory(eq(userAda.getId()), any(), any()))
                .thenAnswer(inv -> {
                    String merch = inv.getArgument(1);
                    TransactionCategory cat = inv.getArgument(2);
                    return inMemoryAdaMemories.stream()
                            .filter(m -> m.getNormalizedMerchant().equals(merch) && m.getCategory() == cat)
                            .findFirst();
                });
        when(memoryRepository.save(any(UserCategorizationMemory.class)))
                .thenAnswer(inv -> {
                    UserCategorizationMemory mem = inv.getArgument(0);
                    inMemoryAdaMemories.removeIf(m -> m.getNormalizedMerchant().equals(mem.getNormalizedMerchant()) && m.getCategory() == mem.getCategory());
                    inMemoryAdaMemories.add(mem);
                    return mem;
                });
        when(feedbackRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userMemoryService = new UserCategorizationMemoryService(memoryRepository, normalizer);
        feedbackService = new CategorizationFeedbackService(feedbackRepository, userMemoryService, normalizer);

        RuleEngine ruleEngine = new RuleEngine(List.of(
                new PayrollAndIncomeRule(),
                new TaxAndGovernmentRule(),
                new BankInterestAndDividendRule(),
                new AtmCashWithdrawalRule(),
                new InternalTransferRule()
        ));

        ClassPathResource artifact = new ClassPathResource("model/baseline_model_v1.json");
        BaselineCategorizationModel baselineModel = new BaselineCategorizationModel(artifact, new com.fasterxml.jackson.databind.ObjectMapper());
        CategorizationConfidencePolicy policy = new CategorizationConfidencePolicy(0.85, 0.60);

        categorizationService = new CategorizationService(
                normalizer,
                ruleEngine,
                userMemoryService,
                baselineModel,
                null,
                policy,
                true,
                false,
                0.60
        );

        transactionService = new TransactionService(
                transactionRepository,
                userRepository,
                currentUser,
                categorizationService,
                feedbackService
        );

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Complete Learning Loop: Prediction -> Correction -> Feedback -> Memory -> Future transactions learn")
    void testEndToEndLearningLoop() {
        // Step 1: User creates first transaction for "Barbeque Nation" with no explicit category
        TransactionRequest req1 = new TransactionRequest(
                new BigDecimal("1200.00"),
                "Barbeque Nation",
                "Dinner buffet",
                null, // Auto-categorization
                TransactionType.EXPENSE,
                PaymentMethod.CREDIT_CARD,
                LocalDate.now(),
                null
        );

        TransactionResponse res1 = transactionService.create(req1);

        // Model initially categorizes as FOOD or OTHER
        TransactionCategory initialCat = res1.category();
        assertThat(initialCat).isNotNull();

        // Simulate saved transaction
        Transaction tx1 = new Transaction(
                userAda,
                req1.amount(),
                req1.merchant(),
                req1.description(),
                res1.category(),
                req1.transactionType(),
                req1.paymentMethod(),
                req1.transactionDate(),
                null,
                BigDecimal.valueOf(res1.confidence() != null ? res1.confidence() : 0.5),
                res1.confidenceLevel(),
                res1.categorizationSource(),
                res1.requiresUserConfirmation(),
                res1.predictedCategory(),
                null
        );
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(tx1));

        // Step 2: User corrects the category to ENTERTAINMENT (e.g. they consider team outings entertainment)
        // Repeat correction 3 times to build strong memory (usageCount >= 3 with 100% agreement)
        for (int i = 0; i < 3; i++) {
            transactionService.correctCategory(tx1.getId(), TransactionCategory.ENTERTAINMENT);
        }

        // Memory should now reflect ENTERTAINMENT as strong preference
        assertThat(inMemoryAdaMemories).isNotEmpty();
        UserCategorizationMemory memory = inMemoryAdaMemories.get(0);
        assertThat(memory.getCategory()).isEqualTo(TransactionCategory.ENTERTAINMENT);
        assertThat(memory.getUsageCount()).isGreaterThanOrEqualTo(3);

        // Step 3: User creates a subsequent transaction for "Barbeque Nation"
        TransactionRequest req2 = new TransactionRequest(
                new BigDecimal("1850.00"),
                "BARBEQUE NATION",
                "Weekend dinner",
                null, // Auto-categorize again
                TransactionType.EXPENSE,
                PaymentMethod.UPI,
                LocalDate.now(),
                null
        );

        TransactionResponse res2 = transactionService.create(req2);

        // Future transaction benefits from user memory: categorized as ENTERTAINMENT with USER_MEMORY source!
        assertThat(res2.category()).isEqualTo(TransactionCategory.ENTERTAINMENT);
        assertThat(res2.categorizationSource()).isEqualTo(CategorizationSource.USER_MEMORY);
        assertThat(res2.requiresUserConfirmation()).isFalse();
    }

    @Test
    @DisplayName("Deterministic rule assigns Payroll to OTHER category without review")
    void testDeterministicRuleAssignment() {
        TransactionRequest req = new TransactionRequest(
                new BigDecimal("60000.00"),
                "ACME INC",
                "MONTHLY SALARY DIRECT DEP",
                null,
                TransactionType.INCOME,
                PaymentMethod.BANK_TRANSFER,
                LocalDate.now(),
                null
        );

        TransactionResponse res = transactionService.create(req);

        assertThat(res.category()).isEqualTo(TransactionCategory.OTHER);
        assertThat(res.categorizationSource()).isEqualTo(CategorizationSource.RULE);
        assertThat(res.confidence()).isEqualTo(1.0);
        assertThat(res.requiresUserConfirmation()).isFalse();
    }
}
