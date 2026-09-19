package com.spendsense.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spendsense.categorization.CategorizationFeedbackCommand;
import com.spendsense.categorization.CategorizationFeedbackService;
import com.spendsense.categorization.CategorizationRequest;
import com.spendsense.categorization.CategorizationResult;
import com.spendsense.categorization.CategorizationService;
import com.spendsense.categorization.CategorizationSource;
import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.common.api.PageMeta;
import com.spendsense.common.exception.ApiException;
import com.spendsense.security.CurrentUser;
import com.spendsense.transaction.dto.CategorizationCandidateDto;
import com.spendsense.transaction.dto.TransactionRequest;
import com.spendsense.transaction.dto.TransactionResponse;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final CategorizationService categorizationService;
    private final CategorizationFeedbackService categorizationFeedbackService;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, CurrentUser currentUser) {
        this(transactionRepository, userRepository, currentUser, null, null);
    }

    @Autowired
    public TransactionService(
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            @Autowired(required = false) CategorizationService categorizationService,
            @Autowired(required = false) CategorizationFeedbackService categorizationFeedbackService
    ) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.categorizationService = categorizationService;
        this.categorizationFeedbackService = categorizationFeedbackService;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        User user = requireUser();
        String merchant = clean(request.merchant());
        String description = cleanOptional(request.description());

        TransactionCategory assignedCategory = request.category();
        Double confidence = null;
        ConfidenceLevel confidenceLevel = null;
        CategorizationSource source = null;
        boolean requiresReview = false;
        TransactionCategory predictedCategory = null;
        List<CategorizationCandidateDto> alternatives = List.of();

        if (assignedCategory != null) {
            // Rule 1: Explicitly user-selected category is respected
            source = CategorizationSource.MANUAL;
            confidenceLevel = ConfidenceLevel.HIGH;
            confidence = 1.0;
            requiresReview = false;
            predictedCategory = assignedCategory;
        } else if (categorizationService != null) {
            // Rule 2-6: Automatically categorized transaction via CategorizationService
            try {
                CategorizationRequest catReq = new CategorizationRequest(
                        user.getId(),
                        merchant,
                        description,
                        request.amount(),
                        request.paymentMethod() != null ? request.paymentMethod().name() : null,
                        request.transactionDate()
                );
                CategorizationResult catResult = categorizationService.categorize(catReq);
                assignedCategory = catResult.category();
                confidence = catResult.confidence();
                confidenceLevel = catResult.confidenceLevel();
                source = catResult.source();
                requiresReview = catResult.requiresUserConfirmation();
                predictedCategory = catResult.category();
                if (catResult.alternatives() != null) {
                    alternatives = catResult.alternatives().stream()
                            .limit(3)
                            .map(CategorizationCandidateDto::from)
                            .toList();
                }
            } catch (Exception e) {
                // Rule 7: Fail-safe degradation
                log.warn("CategorizationService failed safely during transaction creation for merchant '{}': {}", merchant, e.getMessage());
                assignedCategory = TransactionCategory.OTHER;
                confidence = 0.0;
                confidenceLevel = ConfidenceLevel.NONE;
                source = CategorizationSource.FALLBACK;
                requiresReview = true;
                predictedCategory = TransactionCategory.OTHER;
            }
        } else {
            // Default when no categorization service is registered
            assignedCategory = TransactionCategory.OTHER;
            source = CategorizationSource.FALLBACK;
            confidence = 0.0;
            confidenceLevel = ConfidenceLevel.NONE;
            requiresReview = true;
            predictedCategory = TransactionCategory.OTHER;
        }

        Transaction transaction = new Transaction(
                user,
                request.amount(),
                merchant,
                description,
                assignedCategory,
                request.transactionType(),
                request.paymentMethod(),
                request.transactionDate(),
                cleanOptional(request.notes()),
                confidence != null ? BigDecimal.valueOf(confidence) : null,
                confidenceLevel,
                source,
                requiresReview,
                predictedCategory,
                serializeAlternatives(alternatives)
        );

        Transaction saved = transactionRepository.save(transaction);
        return TransactionResponse.from(saved, alternatives);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id) {
        return TransactionResponse.from(findOwned(id));
    }

    @Transactional(readOnly = true)
    public TransactionPage list(int page, int size, TransactionCategory category, TransactionType type,
                                PaymentMethod paymentMethod, LocalDate fromDate, LocalDate toDate,
                                BigDecimal minAmount, BigDecimal maxAmount, String search, String sort) {
        return list(page, size, category, type, paymentMethod, fromDate, toDate, minAmount, maxAmount, search, sort, null);
    }

    @Transactional(readOnly = true)
    public TransactionPage list(int page, int size, TransactionCategory category, TransactionType type,
                                PaymentMethod paymentMethod, LocalDate fromDate, LocalDate toDate,
                                BigDecimal minAmount, BigDecimal maxAmount, String search, String sort,
                                Boolean needsReview) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Specification<Transaction> specification = Specification.where(ownedBy(currentUser.require().getUserId()))
                .and(category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category))
                .and(type == null ? null : (root, query, cb) -> cb.equal(root.get("transactionType"), type))
                .and(paymentMethod == null ? null : (root, query, cb) -> cb.equal(root.get("paymentMethod"), paymentMethod))
                .and(fromDate == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("transactionDate"), fromDate))
                .and(toDate == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("transactionDate"), toDate))
                .and(minAmount == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("amount"), minAmount))
                .and(maxAmount == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("amount"), maxAmount))
                .and(search == null || search.isBlank() ? null : searchFilter(search.trim()))
                .and(needsReview == null ? null : (root, query, cb) -> cb.equal(root.get("requiresReview"), needsReview));

        Page<Transaction> results = transactionRepository.findAll(specification, pageable);
        return new TransactionPage(
                results.map(TransactionResponse::from).getContent(),
                new PageMeta(results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages()));
    }

    @Transactional
    public TransactionResponse update(UUID id, TransactionRequest request) {
        Transaction transaction = findOwned(id);
        TransactionCategory oldCategory = transaction.getCategory();
        TransactionCategory newCategory = request.category() != null ? request.category() : oldCategory;

        transaction.update(
                request.amount(),
                clean(request.merchant()),
                cleanOptional(request.description()),
                newCategory,
                request.transactionType(),
                request.paymentMethod(),
                request.transactionDate(),
                cleanOptional(request.notes()));

        // If the user changed the category during manual edit, record feedback and memory
        if (request.category() != null && request.category() != oldCategory) {
            transaction.setRequiresReview(false);
            transaction.setCategorizationSource(CategorizationSource.MANUAL);
            recordCorrectionFeedback(transaction, newCategory);
        }

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    /**
     * Corrects a transaction's category, updates review state, and records feedback + user memory.
     */
    @Transactional
    public TransactionResponse correctCategory(UUID id, TransactionCategory newCategory) {
        if (newCategory == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY", "Category cannot be null");
        }
        Transaction transaction = findOwned(id);
        transaction.setCategory(newCategory);
        transaction.setRequiresReview(false);
        transaction.setCategorizationSource(CategorizationSource.MANUAL);

        Transaction saved = transactionRepository.save(transaction);
        recordCorrectionFeedback(saved, newCategory);
        return TransactionResponse.from(saved);
    }

    /**
     * Confirms a transaction's current category suggestion, clearing review status and reinforcing memory.
     */
    @Transactional
    public TransactionResponse confirmCategory(UUID id) {
        Transaction transaction = findOwned(id);
        transaction.setRequiresReview(false);

        Transaction saved = transactionRepository.save(transaction);
        recordCorrectionFeedback(saved, saved.getCategory());
        return TransactionResponse.from(saved);
    }

    private void recordCorrectionFeedback(Transaction transaction, TransactionCategory finalCategory) {
        if (categorizationFeedbackService != null) {
            try {
                TransactionCategory originalPredicted = transaction.getPredictedCategory() != null
                        ? transaction.getPredictedCategory()
                        : transaction.getCategory();

                CategorizationFeedbackCommand command = CategorizationFeedbackCommand.builder()
                        .user(transaction.getUser())
                        .transaction(transaction)
                        .rawMerchant(transaction.getMerchant())
                        .rawDescription(transaction.getDescription())
                        .amount(transaction.getAmount())
                        .predictedCategory(originalPredicted)
                        .confidence(transaction.getCategorizationConfidence())
                        .confidenceLevel(transaction.getCategorizationConfidenceLevel())
                        .finalCategory(finalCategory)
                        .predictionSource(transaction.getCategorizationSource() != null ? transaction.getCategorizationSource() : CategorizationSource.MANUAL)
                        .updateMemory(true)
                        .build();

                categorizationFeedbackService.recordFeedback(command);
            } catch (Exception e) {
                log.warn("Failed to record categorization feedback for transaction {}: {}", transaction.getId(), e.getMessage());
            }
        }
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

    private static String serializeAlternatives(List<CategorizationCandidateDto> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(alternatives);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize categorization alternatives: {}", e.getMessage());
            return null;
        }
    }

    private static String clean(String value) { return value.trim(); }
    private static String cleanOptional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record TransactionPage(List<TransactionResponse> content, PageMeta meta) {
    }
}
