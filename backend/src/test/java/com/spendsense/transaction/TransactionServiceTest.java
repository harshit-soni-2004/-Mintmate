package com.spendsense.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.categorization.CategorizationFeedbackCommand;
import com.spendsense.categorization.CategorizationFeedbackService;
import com.spendsense.categorization.CategorizationRequest;
import com.spendsense.categorization.CategorizationResult;
import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.categorization.model.CategorizationCandidate;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.security.SpendSenseUserPrincipal;
import com.spendsense.transaction.dto.TransactionRequest;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;
    @Mock private CategorizationService categorizationService;
    @Mock private CategorizationFeedbackService categorizationFeedbackService;

    private User user;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        user = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        service = new TransactionService(transactionRepository, userRepository, currentUser, categorizationService, categorizationFeedbackService);
    }

    @Nested
    @DisplayName("Transaction Creation & Categorization Integration")
    class CreationTests {

        @Test
        @DisplayName("Explicit user-selected category must be respected without calling CategorizationService")
        void createsTransactionWithExplicitCategory() {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            var req = new TransactionRequest(
                    new BigDecimal("500.00"),
                    "  Swiggy  ",
                    "Dinner delivery",
                    TransactionCategory.FOOD,
                    TransactionType.EXPENSE,
                    PaymentMethod.UPI,
                    LocalDate.of(2026, 9, 8),
                    "Spicy biryani"
            );

            var response = service.create(req);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            Transaction saved = captor.getValue();
            assertThat(saved.getUser()).isSameAs(user);
            assertThat(saved.getMerchant()).isEqualTo("Swiggy");
            assertThat(saved.getCategory()).isEqualTo(TransactionCategory.FOOD);
            assertThat(saved.getCategorizationSource()).isEqualTo(CategorizationSource.MANUAL);
            assertThat(saved.isRequiresReview()).isFalse();
            assertThat(response.category()).isEqualTo(TransactionCategory.FOOD);
            verify(categorizationService, never()).categorize(any(CategorizationRequest.class));
        }

        @Test
        @DisplayName("Null category triggers automatic CategorizationService with HIGH confidence")
        void createsTransactionWithAutoCategorizationHighConfidence() {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            CategorizationResult highResult = CategorizationResult.fromMl(
                    TransactionCategory.FOOD,
                    0.95,
                    ConfidenceLevel.HIGH,
                    List.of(new CategorizationCandidate(TransactionCategory.FOOD, 0.95),
                            new CategorizationCandidate(TransactionCategory.SHOPPING, 0.03)),
                    "v1.0.0-baseline-tfidf-lr",
                    false,
                    false, // requiresUserConfirmation = false
                    "Global ML high confidence",
                    "swiggy"
            );
            when(categorizationService.categorize(any(CategorizationRequest.class))).thenReturn(highResult);

            var req = new TransactionRequest(
                    new BigDecimal("350.00"),
                    "Swiggy",
                    "Dinner delivery",
                    null, // omitted category -> triggers auto-categorization
                    TransactionType.EXPENSE,
                    PaymentMethod.UPI,
                    LocalDate.of(2026, 9, 8),
                    null
            );

            var response = service.create(req);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            Transaction saved = captor.getValue();
            assertThat(saved.getCategory()).isEqualTo(TransactionCategory.FOOD);
            assertThat(saved.getCategorizationConfidence().doubleValue()).isEqualTo(0.95);
            assertThat(saved.getCategorizationConfidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(saved.getCategorizationSource()).isEqualTo(CategorizationSource.ML);
            assertThat(saved.isRequiresReview()).isFalse();
            assertThat(saved.getPredictedCategory()).isEqualTo(TransactionCategory.FOOD);

            assertThat(response.category()).isEqualTo(TransactionCategory.FOOD);
            assertThat(response.confidence()).isEqualTo(0.95);
            assertThat(response.confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
            assertThat(response.requiresUserConfirmation()).isFalse();
            assertThat(response.alternatives()).hasSize(2);
        }

        @Test
        @DisplayName("Medium confidence ML prediction sets requiresReview = true")
        void createsTransactionWithMediumConfidenceRequiresReview() {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            CategorizationResult medResult = CategorizationResult.fromMl(
                    TransactionCategory.ENTERTAINMENT,
                    0.72,
                    ConfidenceLevel.MEDIUM,
                    List.of(new CategorizationCandidate(TransactionCategory.ENTERTAINMENT, 0.72),
                            new CategorizationCandidate(TransactionCategory.SHOPPING, 0.20)),
                    "v1.0.0-baseline-tfidf-lr",
                    false,
                    true, // requiresUserConfirmation = true
                    "Confirmation recommended",
                    "mystery box"
            );
            when(categorizationService.categorize(any(CategorizationRequest.class))).thenReturn(medResult);

            var req = new TransactionRequest(
                    new BigDecimal("150.00"),
                    "Mystery Box",
                    "Fun arcade",
                    null,
                    TransactionType.EXPENSE,
                    PaymentMethod.CREDIT_CARD,
                    LocalDate.of(2026, 9, 8),
                    null
            );

            var response = service.create(req);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            Transaction saved = captor.getValue();
            assertThat(saved.getCategory()).isEqualTo(TransactionCategory.ENTERTAINMENT);
            assertThat(saved.isRequiresReview()).isTrue();
            assertThat(response.requiresUserConfirmation()).isTrue();
            assertThat(response.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        }

        @Test
        @DisplayName("CategorizationService failure falls back safely to OTHER without failing transaction creation")
        void createsTransactionSafelyWhenCategorizationFails() {
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(categorizationService.categorize(any(CategorizationRequest.class)))
                    .thenThrow(new RuntimeException("ML service failure"));

            var req = new TransactionRequest(
                    new BigDecimal("80.00"),
                    "Corrupt Merchant",
                    null,
                    null,
                    TransactionType.EXPENSE,
                    PaymentMethod.CASH,
                    LocalDate.of(2026, 9, 8),
                    null
            );

            var response = service.create(req);

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository).save(captor.capture());
            Transaction saved = captor.getValue();
            assertThat(saved.getCategory()).isEqualTo(TransactionCategory.OTHER);
            assertThat(saved.getCategorizationSource()).isEqualTo(CategorizationSource.FALLBACK);
            assertThat(saved.isRequiresReview()).isTrue();
            assertThat(saved.getCategorizationConfidence().doubleValue()).isEqualTo(0.0);
            assertThat(response.category()).isEqualTo(TransactionCategory.OTHER);
            assertThat(response.requiresUserConfirmation()).isTrue();
        }
    }

    @Nested
    @DisplayName("Category Correction & Feedback API")
    class CorrectionAndFeedbackTests {

        @Test
        @DisplayName("Category correction updates transaction, clears review, and records feedback")
        void correctsCategorySuccessfully() {
            Transaction transaction = transaction();
            transaction.setCategory(TransactionCategory.OTHER);
            transaction.setPredictedCategory(TransactionCategory.OTHER);
            transaction.setRequiresReview(true);
            transaction.setCategorizationConfidence(BigDecimal.valueOf(0.45));
            transaction.setCategorizationConfidenceLevel(ConfidenceLevel.LOW);
            transaction.setCategorizationSource(CategorizationSource.FALLBACK);

            when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            var response = service.correctCategory(transaction.getId(), TransactionCategory.FOOD);

            assertThat(response.category()).isEqualTo(TransactionCategory.FOOD);
            assertThat(response.requiresUserConfirmation()).isFalse();
            assertThat(response.categorizationSource()).isEqualTo(CategorizationSource.MANUAL);

            ArgumentCaptor<CategorizationFeedbackCommand> cmdCaptor = ArgumentCaptor.forClass(CategorizationFeedbackCommand.class);
            verify(categorizationFeedbackService).recordFeedback(cmdCaptor.capture());
            CategorizationFeedbackCommand cmd = cmdCaptor.getValue();
            assertThat(cmd.finalCategory()).isEqualTo(TransactionCategory.FOOD);
            assertThat(cmd.predictedCategory()).isEqualTo(TransactionCategory.OTHER);
            assertThat(cmd.updateMemory()).isTrue();
            assertThat(cmd.rawMerchant()).isEqualTo("Swiggy");
        }

        @Test
        @DisplayName("Category confirmation clears review state and records acceptance feedback")
        void confirmsCategorySuccessfully() {
            Transaction transaction = transaction();
            transaction.setCategory(TransactionCategory.FOOD);
            transaction.setPredictedCategory(TransactionCategory.FOOD);
            transaction.setRequiresReview(true);

            when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(transaction));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

            var response = service.confirmCategory(transaction.getId());

            assertThat(response.category()).isEqualTo(TransactionCategory.FOOD);
            assertThat(response.requiresUserConfirmation()).isFalse();

            ArgumentCaptor<CategorizationFeedbackCommand> cmdCaptor = ArgumentCaptor.forClass(CategorizationFeedbackCommand.class);
            verify(categorizationFeedbackService).recordFeedback(cmdCaptor.capture());
            CategorizationFeedbackCommand cmd = cmdCaptor.getValue();
            assertThat(cmd.finalCategory()).isEqualTo(TransactionCategory.FOOD);
            assertThat(cmd.predictedCategory()).isEqualTo(TransactionCategory.FOOD);
            assertThat(cmd.updateMemory()).isTrue();
        }

        @Test
        @DisplayName("User A cannot correct User B's transaction")
        void anotherUserCannotCorrectCategory() {
            when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

            assertNotFound(() -> service.correctCategory(UUID.randomUUID(), TransactionCategory.SHOPPING));
            verify(categorizationFeedbackService, never()).recordFeedback(any());
        }

        @Test
        @DisplayName("Null category correction is rejected with BAD_REQUEST")
        void rejectsNullCategoryCorrection() {
            assertThatThrownBy(() -> service.correctCategory(UUID.randomUUID(), null))
                    .isInstanceOf(ApiException.class)
                    .extracting("code")
                    .isEqualTo("INVALID_CATEGORY");
        }
    }

    @Nested
    @DisplayName("Review Filtering & Querying")
    class ReviewFilterTests {

        @Test
        @DisplayName("Queries transactions filtering by needsReview = true")
        void filtersTransactionsNeedingReview() {
            Transaction transaction = transaction();
            transaction.setRequiresReview(true);
            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(transaction), org.springframework.data.domain.PageRequest.of(0, 20), 1));

            var page = service.list(
                    0, 20, null, null, null, null, null, null, null, null, null, true
            );

            assertThat(page.content()).hasSize(1);
            assertThat(page.content().get(0).requiresUserConfirmation()).isTrue();
        }
    }

    @Test
    void createsIncomeTransactionForAuthenticatedUser() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var req = new TransactionRequest(
                new BigDecimal("50000.00"),
                "Acme Corp",
                "Monthly Salary",
                TransactionCategory.OTHER,
                TransactionType.INCOME,
                PaymentMethod.BANK_TRANSFER,
                LocalDate.of(2026, 9, 1),
                "Direct deposit"
        );

        var response = service.create(req);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction saved = captor.getValue();
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.INCOME);
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(response.merchant()).isEqualTo("Acme Corp");
    }

    @Test
    void retrievesOwnedTransaction() {
        Transaction transaction = transaction();
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(transaction));

        var response = service.get(transaction.getId());
        assertThat(response.merchant()).isEqualTo("Swiggy");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.UPI);
    }

    @Test
    void updatesOwnedTransaction() {
        Transaction transaction = transaction();
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(transaction));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        var req = new TransactionRequest(
                new BigDecimal("750.00"),
                "Zomato",
                "Late dinner",
                TransactionCategory.FOOD,
                TransactionType.EXPENSE,
                PaymentMethod.CREDIT_CARD,
                LocalDate.of(2026, 9, 8),
                "Edited note"
        );

        var response = service.update(transaction.getId(), req);

        assertThat(response.merchant()).isEqualTo("Zomato");
        assertThat(response.amount()).isEqualByComparingTo("750.00");
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        verify(transactionRepository).save(transaction);
    }

    @Test
    void deletesOwnedTransaction() {
        Transaction transaction = transaction();
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.of(transaction));

        service.delete(transaction.getId());

        verify(transactionRepository).delete(transaction);
    }

    @Test
    void returnsPageMetadataWithFiltersAndSorting() {
        Transaction transaction = transaction();
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction), org.springframework.data.domain.PageRequest.of(0, 20), 1));

        var page = service.list(
                0,
                20,
                TransactionCategory.FOOD,
                TransactionType.EXPENSE,
                PaymentMethod.UPI,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                new BigDecimal("100.00"),
                new BigDecimal("1000.00"),
                "swig",
                "highest_amount"
        );

        assertThat(page.content()).hasSize(1);
        assertThat(page.meta().page()).isEqualTo(0);
        assertThat(page.meta().totalElements()).isEqualTo(1);
        assertThat(page.meta().first()).isTrue();
        assertThat(page.meta().last()).isTrue();
    }

    @Test
    void anotherUserCannotRetrieveTransaction() {
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertNotFound(() -> service.get(transaction().getId()));
    }

    @Test
    void anotherUserCannotUpdateTransaction() {
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertNotFound(() -> service.update(transaction().getId(), request("Changed")));
    }

    @Test
    void anotherUserCannotDeleteTransaction() {
        when(transactionRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());

        assertNotFound(() -> service.delete(transaction().getId()));
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ApiException.class).extracting("code").isEqualTo("TRANSACTION_NOT_FOUND");
    }

    private Transaction transaction() {
        return new Transaction(
                user,
                new BigDecimal("420.00"),
                "Swiggy",
                "Lunch",
                TransactionCategory.FOOD,
                TransactionType.EXPENSE,
                PaymentMethod.UPI,
                LocalDate.of(2026, 9, 5),
                null
        );
    }

    private TransactionRequest request(String merchant) {
        return new TransactionRequest(
                new BigDecimal("420.00"),
                merchant,
                null,
                TransactionCategory.FOOD,
                TransactionType.EXPENSE,
                PaymentMethod.UPI,
                LocalDate.of(2026, 9, 5),
                null
        );
    }
}
