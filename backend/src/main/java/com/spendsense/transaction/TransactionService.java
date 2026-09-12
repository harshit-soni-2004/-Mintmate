package com.spendsense.transaction;

import com.spendsense.common.api.PageMeta;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import com.spendsense.transaction.dto.TransactionRequest;
import com.spendsense.transaction.dto.TransactionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, CurrentUser currentUser) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Transaction transaction = new Transaction(
                requireUser(),
                request.amount(),
                clean(request.merchant()),
                cleanOptional(request.description()),
                request.category(),
                request.transactionType(),
                request.paymentMethod(),
                request.transactionDate(),
                cleanOptional(request.notes()));
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id) {
        return TransactionResponse.from(findOwned(id));
    }

    @Transactional(readOnly = true)
    public TransactionPage list(int page, int size, TransactionCategory category, TransactionType type,
                                PaymentMethod paymentMethod, LocalDate fromDate, LocalDate toDate,
                                BigDecimal minAmount, BigDecimal maxAmount, String search, String sort) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<Transaction> specification = Specification.where(ownedBy(currentUser.require().getUserId()))
                .and(category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category))
                .and(type == null ? null : (root, query, cb) -> cb.equal(root.get("transactionType"), type))
                .and(paymentMethod == null ? null : (root, query, cb) -> cb.equal(root.get("paymentMethod"), paymentMethod))
                .and(fromDate == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), fromDate))
                .and(toDate == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), toDate))
                .and(minAmount == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount))
                .and(maxAmount == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount))
                .and(search == null || search.isBlank() ? null : searchFilter(search.trim()));

        Page<Transaction> results = transactionRepository.findAll(specification, pageable);
        return new TransactionPage(
                results.map(TransactionResponse::from).getContent(),
                new PageMeta(results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages()));
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request) {
        Transaction transaction = findOwned(id);
        transaction.update(
                request.amount(),
                clean(request.merchant()),
                cleanOptional(request.description()),
                request.category(),
                request.transactionType(),
                request.paymentMethod(),
                request.transactionDate(),
                cleanOptional(request.notes()));
        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID id) {
        transactionRepository.delete(findOwned(id));
    }

    private Transaction findOwned(UUID id) {
        UUID userId = currentUser.require().getUserId();
        return transactionRepository.findOne(Specification.where(ownedBy(userId))
                        .and((root, query, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found"));
    }

    private User requireUser() {
        UUID userId = currentUser.require().getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }

    private static Specification<Transaction> ownedBy(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    private static Specification<Transaction> searchFilter(String search) {
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("merchant")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("description"), "")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern)
        );
    }

    private static Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("newest") || sort.equalsIgnoreCase("transactionDate,desc")) {
            return Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt"));
        }
        if (sort.equalsIgnoreCase("oldest") || sort.equalsIgnoreCase("transactionDate,asc")) {
            return Sort.by(Sort.Order.asc("transactionDate"), Sort.Order.asc("createdAt"));
        }
        if (sort.equalsIgnoreCase("highest_amount") || sort.equalsIgnoreCase("amount,desc")) {
            return Sort.by(Sort.Order.desc("amount"), Sort.Order.desc("transactionDate"));
        }
        if (sort.equalsIgnoreCase("lowest_amount") || sort.equalsIgnoreCase("amount,asc")) {
            return Sort.by(Sort.Order.asc("amount"), Sort.Order.desc("transactionDate"));
        }

        String[] parts = sort.split(",");
        String prop = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, prop);
    }

    private static String clean(String value) { return value.trim(); }
    private static String cleanOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record TransactionPage(List<TransactionResponse> content, PageMeta meta) {
    }
}
