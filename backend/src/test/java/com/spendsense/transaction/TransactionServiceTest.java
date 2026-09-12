package com.spendsense.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private CurrentUser currentUser;
    private User user;
    private TransactionService service;

    @BeforeEach
    void setUp() {
        user = new User("ada@example.com", "hash", "Ada", "Asia/Kolkata");
        when(currentUser.require()).thenReturn(new SpendSenseUserPrincipal(user));
        service = new TransactionService(transactionRepository, userRepository, currentUser);
    }

    @Test
    void createsExpenseTransactionForAuthenticatedUser() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        assertThat(saved.getDescription()).isEqualTo("Dinner delivery");
        assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(response.amount()).isEqualByComparingTo("500.00");
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
