package com.spendsense.debt;

import com.spendsense.common.exception.ApiException;
import com.spendsense.debt.dto.CreateDebtRequest;
import com.spendsense.debt.dto.DebtCandidateTransactionDto;
import com.spendsense.debt.dto.DebtDto;
import com.spendsense.debt.dto.DebtSummaryDto;
import com.spendsense.debt.dto.SettleDebtRequest;
import com.spendsense.security.CurrentUser;
import com.spendsense.transaction.IncomeTier;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.transaction.TransferDirection;
import com.spendsense.transaction.TransferMatchStatus;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DebtService {

    private static final Logger log = LoggerFactory.getLogger(DebtService.class);

    public static final int CREATED_AT_LOOKBACK_DAYS = 7;
    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private final DebtRepository debtRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final DebtMatchDismissalRepository dismissalRepository;
    private final com.spendsense.account.FinancialAccountRepository accountRepository;

    public DebtService(
            DebtRepository debtRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUser currentUser
    ) {
        this(debtRepository, transactionRepository, userRepository, currentUser, null, null);
    }

    public DebtService(
            DebtRepository debtRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            DebtMatchDismissalRepository dismissalRepository
    ) {
        this(debtRepository, transactionRepository, userRepository, currentUser, dismissalRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DebtService(
            DebtRepository debtRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            @org.springframework.beans.factory.annotation.Autowired(required = false) DebtMatchDismissalRepository dismissalRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.spendsense.account.FinancialAccountRepository accountRepository
    ) {
        this.debtRepository = debtRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.dismissalRepository = dismissalRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public DebtDto createDebt(CreateDebtRequest request) {
        User user = requireUser();

        Debt debt = new Debt(
                user,
                request.counterpartyName().trim(),
                request.debtType(),
                request.amount(),
                request.dueDate(),
                request.notes()
        );

        Debt saved = debtRepository.save(debt);
        log.info("Created debt {} for user {} ({}: {})", saved.getId(), user.getId(), saved.getDebtType(), saved.getAmount());
        return DebtDto.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<DebtDto> getDebts(DebtStatus status) {
        User user = requireUser();
        List<Debt> debts = (status != null)
                ? debtRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), status)
                : debtRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return debts.stream().map(DebtDto::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public DebtDto getDebt(UUID id) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));
        return DebtDto.fromEntity(debt);
    }

    @Transactional(readOnly = true)
    public DebtSummaryDto getDebtSummary() {
        User user = requireUser();
        LocalDate today = LocalDate.now();

        BigDecimal totalReceivable = debtRepository.sumActiveAmountByUserIdAndDebtType(user.getId(), DebtType.OWED_TO_ME);
        BigDecimal totalPayable = debtRepository.sumActiveAmountByUserIdAndDebtType(user.getId(), DebtType.I_OWE);
        BigDecimal netDebtPosition = totalReceivable.subtract(totalPayable);

        List<Debt> overdueList = debtRepository.findOverdueDebts(user.getId(), today);
        List<Debt> upcomingList = debtRepository.findUpcomingDebts(user.getId(), today);
        List<Debt> allActive = debtRepository.findByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), DebtStatus.ACTIVE);

        return new DebtSummaryDto(
                totalReceivable,
                totalPayable,
                netDebtPosition,
                allActive.size(),
                overdueList.size(),
                upcomingList.stream().map(DebtDto::fromEntity).toList(),
                overdueList.stream().map(DebtDto::fromEntity).toList()
        );
    }

    /**
     * Finds and ranks candidate transactions for linking to a debt.
     * All matching and ranking rules live here:
     * - Scoped strictly to authenticated user
     * - Lookback: transactionDate >= createdAt date minus CREATED_AT_LOOKBACK_DAYS (never uses dueDate)
     * - Direction: OWED_TO_ME requires incoming credits (INCOME or TRANSFER IN); I_OWE requires outgoing debits (EXPENSE or TRANSFER OUT)
     * - Excludes transactions already linked to any debt
     * - Excludes transactions dismissed by user for this debt
     * - Settled-without-link debts: suggested only with counterparty text match OR within 60 days after settled_at
     * - Ranked by exact amount + counterparty > exact amount > close amount > recent date
     */
    @Transactional(readOnly = true)
    public List<DebtCandidateTransactionDto> getCandidateTransactions(UUID debtId) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));

        return computeCandidates(user, debt);
    }

    public List<DebtCandidateTransactionDto> computeCandidates(User user, Debt debt) {
        if (debt == null || debt.getStatus() == DebtStatus.CANCELLED) {
            return Collections.emptyList();
        }
        if (debt.getStatus() == DebtStatus.SETTLED && debt.getLinkedTransaction() != null) {
            return Collections.emptyList();
        }

        LocalDate refDate = debt.getCreatedAt() != null
                ? debt.getCreatedAt().atZone(IST_ZONE).toLocalDate()
                : LocalDate.now(IST_ZONE);
        LocalDate earliestDate = refDate.minusDays(CREATED_AT_LOOKBACK_DAYS);
        LocalDate settledDate = debt.getSettledAt() != null
                ? debt.getSettledAt().atZone(IST_ZONE).toLocalDate()
                : null;

        List<UUID> linkedIdsList = debtRepository.findLinkedTransactionIdsByUserId(user.getId());
        Set<UUID> linkedIds = new HashSet<>(linkedIdsList != null ? linkedIdsList : List.of());
        if (debt.getLinkedTransaction() != null) {
            linkedIds.remove(debt.getLinkedTransaction().getId());
        }

        Set<UUID> dismissedIds = new HashSet<>();
        if (dismissalRepository != null) {
            List<UUID> dismissed = dismissalRepository.findDismissedTransactionIdsByUserIdAndDebtId(user.getId(), debt.getId());
            if (dismissed != null) {
                dismissedIds.addAll(dismissed);
            }
        }

        List<Transaction> rawCandidates;
        if (debt.getDebtType() == DebtType.OWED_TO_ME) {
            rawCandidates = transactionRepository.findCandidateCreditsForDebt(user.getId(), earliestDate);
        } else {
            rawCandidates = transactionRepository.findCandidateDebitsForDebt(user.getId(), earliestDate);
        }

        List<Transaction> eligible = rawCandidates.stream()
                .filter(t -> t.getTransactionDate() != null && !t.getTransactionDate().isBefore(earliestDate))
                .filter(t -> !linkedIds.contains(t.getId()))
                .filter(t -> !dismissedIds.contains(t.getId()))
                .filter(t -> {
                    if (debt.getStatus() == DebtStatus.SETTLED && debt.getLinkedTransaction() == null) {
                        boolean counterpartyMatch = matchesCounterparty(debt.getCounterpartyName(), t.getMerchant(), t.getDescription());
                        boolean within60Days = settledDate != null && !t.getTransactionDate().isAfter(settledDate.plusDays(60));
                        return counterpartyMatch || within60Days;
                    }
                    return true;
                })
                .toList();

        List<DebtCandidateTransactionDto> scoredCandidates = new ArrayList<>();
        BigDecimal targetAmount = debt.getAmount();

        for (Transaction t : eligible) {
            int baseScore;
            String reason;

            boolean exactAmount = targetAmount != null && t.getAmount() != null && targetAmount.compareTo(t.getAmount()) == 0;
            boolean counterpartyMatch = matchesCounterparty(debt.getCounterpartyName(), t.getMerchant(), t.getDescription());
            boolean closeAmount = !exactAmount && isCloseAmount(targetAmount, t.getAmount());
            boolean isStrongMatch = exactAmount && counterpartyMatch;

            if (exactAmount && counterpartyMatch) {
                baseScore = 100;
                reason = "Exact amount and counterparty match";
            } else if (exactAmount) {
                baseScore = 75;
                reason = "Exact amount match";
            } else if (closeAmount && counterpartyMatch) {
                baseScore = 55;
                reason = "Close amount and counterparty match";
            } else if (closeAmount) {
                baseScore = 40;
                reason = "Close amount match";
            } else if (counterpartyMatch) {
                baseScore = 30;
                reason = "Counterparty name match";
            } else {
                baseScore = 10;
                reason = "Recent candidate";
            }

            long daysDiff = Math.abs(ChronoUnit.DAYS.between(refDate, t.getTransactionDate()));
            int recencyBonus = (int) Math.max(0, 10 - Math.min(10, daysDiff));
            int totalScore = (baseScore * 100) + recencyBonus;

            scoredCandidates.add(new DebtCandidateTransactionDto(
                    t.getId(),
                    t.getAmount(),
                    t.getDescription(),
                    t.getMerchant(),
                    t.getTransactionDate(),
                    t.getTransactionType(),
                    t.getTransferDirection(),
                    false,
                    isStrongMatch,
                    reason,
                    totalScore
            ));
        }

        scoredCandidates.sort(Comparator
                .comparingInt(DebtCandidateTransactionDto::score)
                .reversed()
                .thenComparing(DebtCandidateTransactionDto::transactionDate, Comparator.reverseOrder()));

        if (!scoredCandidates.isEmpty() && scoredCandidates.get(0).score() >= 3000) {
            DebtCandidateTransactionDto top = scoredCandidates.get(0);
            scoredCandidates.set(0, new DebtCandidateTransactionDto(
                    top.id(),
                    top.amount(),
                    top.description(),
                    top.merchant(),
                    top.transactionDate(),
                    top.transactionType(),
                    top.transferDirection(),
                    true,
                    top.isStrongMatch(),
                    top.matchReason(),
                    top.score()
            ));
        }

        return scoredCandidates;
    }

    private boolean isCloseAmount(BigDecimal target, BigDecimal candidate) {
        if (target == null || candidate == null) return false;
        BigDecimal diff = target.subtract(candidate).abs();
        BigDecimal tenPercent = target.multiply(BigDecimal.valueOf(0.10));
        if (diff.compareTo(tenPercent) <= 0) {
            return true;
        }
        return diff.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    private boolean matchesCounterparty(String counterparty, String merchant, String description) {
        if (counterparty == null || counterparty.isBlank()) return false;
        String cp = counterparty.trim().toLowerCase();

        String m = merchant != null ? merchant.toLowerCase() : "";
        String d = description != null ? description.toLowerCase() : "";

        if (m.contains(cp) || d.contains(cp)) {
            return true;
        }
        String[] tokens = cp.split("\\s+");
        for (String token : tokens) {
            if (token.length() >= 3 && (m.contains(token) || d.contains(token))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Settles an active debt entry.
     * When linked to a transaction, reconciles without double-counting:
     * - If OWED_TO_ME: Repayment credit is excluded from Income metrics (reclassified as TRANSFER IN)
     * - If I_OWE: Repayment debit is reclassified to prevent inflating discretionary category budgets (reclassified as TRANSFER OUT)
     */
    @Transactional
    public DebtDto settleDebt(UUID debtId, SettleDebtRequest settleRequest) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));

        if (debt.getStatus() == DebtStatus.SETTLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEBT_ALREADY_SETTLED", "Debt is already settled. Use the link endpoint to attach a transaction.");
        }
        if (debt.getStatus() == DebtStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEBT_CANCELLED", "Cannot settle a cancelled debt");
        }

        Transaction settlementTx = null;
        if (settleRequest != null && settleRequest.linkedTransactionId() != null) {
            settlementTx = transactionRepository.findById(settleRequest.linkedTransactionId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Settlement transaction not found"));

            if (!settlementTx.getUser().getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot link transaction belonging to another user");
            }

            reconcileSettlementTransaction(debt, settlementTx);
        }

        debt.settle(settlementTx);
        Debt updated = debtRepository.save(debt);
        log.info("Settled debt {} for user {}, linked tx: {}", debtId, user.getId(),
                settlementTx != null ? settlementTx.getId() : "none");
        return DebtDto.fromEntity(updated);
    }

    /**
     * Records a payment transaction and settles the debt entry in one atomic operation.
     * The transaction is created as a TRANSFER (IN for OWED_TO_ME, OUT for I_OWE) with category OTHER.
     * Income, expenses, and savings rate remain unchanged.
     */
    @Transactional
    public DebtDto recordPaymentAndSettle(UUID debtId, com.spendsense.debt.dto.RecordPaymentAndSettleRequest request) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));

        if (debt.getStatus() == DebtStatus.SETTLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEBT_ALREADY_SETTLED", "Debt is already settled");
        }
        if (debt.getStatus() == DebtStatus.CANCELLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEBT_CANCELLED", "Cannot settle a cancelled debt");
        }

        if (request == null || request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Amount must be greater than zero");
        }

        LocalDate todayIst = LocalDate.now(IST_ZONE);
        if (request.transactionDate() == null || request.transactionDate().isAfter(todayIst)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "Transaction date cannot be in the future");
        }

        com.spendsense.account.FinancialAccount account = null;
        if (request.accountId() != null) {
            if (accountRepository == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_FOUND", "Account repository unavailable");
            }
            account = accountRepository.findByIdAndUserId(request.accountId(), user.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ACCOUNT_NOT_FOUND", "Selected account not found or does not belong to user"));
        }

        String defaultNote = debt.getDebtType() == DebtType.OWED_TO_ME
                ? "Repayment from " + debt.getCounterpartyName()
                : "Payment to " + debt.getCounterpartyName();
        String note = (request.notes() != null && !request.notes().isBlank()) ? request.notes().trim() : defaultNote;

        Transaction tx;
        if (debt.getDebtType() == DebtType.OWED_TO_ME) {
            tx = new Transaction(
                    user,
                    request.amount(),
                    debt.getCounterpartyName(),
                    defaultNote,
                    TransactionCategory.OTHER,
                    TransactionType.TRANSFER,
                    com.spendsense.transaction.PaymentMethod.OTHER,
                    request.transactionDate(),
                    note,
                    BigDecimal.ONE,
                    com.spendsense.categorization.ConfidenceLevel.HIGH,
                    com.spendsense.categorization.CategorizationSource.MANUAL,
                    false,
                    TransactionCategory.OTHER,
                    null,
                    com.spendsense.transaction.TransactionSourceType.MANUAL,
                    null,
                    "debt:repayment:" + debt.getId(),
                    account,
                    TransferDirection.IN,
                    null,
                    null,
                    TransferMatchStatus.NONE
            );
            tx.setIncomeTier(IncomeTier.TIER_3_REFUND);
            tx.setIncomeVerified(false);
        } else {
            tx = new Transaction(
                    user,
                    request.amount(),
                    debt.getCounterpartyName(),
                    defaultNote,
                    TransactionCategory.OTHER,
                    TransactionType.TRANSFER,
                    com.spendsense.transaction.PaymentMethod.OTHER,
                    request.transactionDate(),
                    note,
                    BigDecimal.ONE,
                    com.spendsense.categorization.ConfidenceLevel.HIGH,
                    com.spendsense.categorization.CategorizationSource.MANUAL,
                    false,
                    TransactionCategory.OTHER,
                    null,
                    com.spendsense.transaction.TransactionSourceType.MANUAL,
                    null,
                    "debt:repayment:" + debt.getId(),
                    account,
                    TransferDirection.OUT,
                    null,
                    null,
                    TransferMatchStatus.NONE
            );
        }

        Transaction savedTx = transactionRepository.save(tx);
        debt.settle(savedTx);
        Debt updated = debtRepository.save(debt);
        log.info("Recorded payment transaction {} and settled debt {} for user {}", savedTx.getId(), debtId, user.getId());
        return DebtDto.fromEntity(updated);
    }

    /**
     * Dedicated link-only path for debts already marked settled without a transaction link.
     * Reuses the existing TRANSFER reclassification.
     */
    @Transactional
    public DebtDto linkTransactionToSettledDebt(UUID debtId, SettleDebtRequest request) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));

        if (debt.getStatus() != DebtStatus.SETTLED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEBT_NOT_SETTLED", "Debt is not settled. Use settle endpoint to settle an active debt.");
        }
        if (debt.getLinkedTransaction() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEBT_ALREADY_LINKED", "Debt is already linked to a transaction");
        }
        if (request == null || request.linkedTransactionId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRANSACTION_REQUIRED", "A transaction ID is required to link to a settled debt");
        }

        Transaction settlementTx = transactionRepository.findById(request.linkedTransactionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Settlement transaction not found"));

        if (!settlementTx.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot link transaction belonging to another user");
        }

        reconcileSettlementTransaction(debt, settlementTx);
        debt.setLinkedTransaction(settlementTx);
        Debt updated = debtRepository.save(debt);
        log.info("Linked transaction {} to settled debt {} for user {}", settlementTx.getId(), debtId, user.getId());
        return DebtDto.fromEntity(updated);
    }

    private void reconcileSettlementTransaction(Debt debt, Transaction settlementTx) {
        if (debt.getDebtType() == DebtType.OWED_TO_ME) {
            settlementTx.setIncomeTier(IncomeTier.TIER_3_REFUND);
            settlementTx.setCategory(TransactionCategory.OTHER);
            if (settlementTx.getTransactionType() == TransactionType.INCOME) {
                settlementTx.setTransactionType(TransactionType.TRANSFER);
                settlementTx.setTransferDirection(TransferDirection.IN);
            }
        } else {
            settlementTx.setCategory(TransactionCategory.OTHER);
            if (settlementTx.getTransactionType() == TransactionType.EXPENSE) {
                settlementTx.setTransactionType(TransactionType.TRANSFER);
                settlementTx.setTransferDirection(TransferDirection.OUT);
            }
        }
        transactionRepository.save(settlementTx);
    }

    @Transactional
    public void dismissCandidate(UUID debtId, UUID transactionId) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found"));

        if (!tx.getUser().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot dismiss transaction belonging to another user");
        }

        if (dismissalRepository != null && !dismissalRepository.existsByUserIdAndDebtIdAndTransactionId(user.getId(), debtId, transactionId)) {
            DebtMatchDismissal dismissal = new DebtMatchDismissal(user, debt, tx);
            dismissalRepository.save(dismissal);
            log.info("Persisted candidate dismissal for user {}, debt {}, tx {}", user.getId(), debtId, transactionId);
        }
    }

    @Transactional(readOnly = true)
    public int evaluateSuggestionsAfterIngestion(UUID userId, List<Transaction> importedTransactions) {
        if (userId == null || importedTransactions == null || importedTransactions.isEmpty()) {
            return 0;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return 0;
        }

        List<Debt> candidateDebts = debtRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(d -> d.getStatus() == DebtStatus.ACTIVE || (d.getStatus() == DebtStatus.SETTLED && d.getLinkedTransaction() == null))
                .toList();

        int suggestionsFound = 0;
        for (Debt debt : candidateDebts) {
            List<DebtCandidateTransactionDto> candidates = computeCandidates(user, debt);
            if (!candidates.isEmpty()) {
                suggestionsFound += candidates.size();
            }
        }

        log.info("Evaluated debt suggestions after statement import for user {}: found {} candidate matches across {} eligible debts",
                userId, suggestionsFound, candidateDebts.size());
        return suggestionsFound;
    }

    @Transactional
    public DebtDto cancelDebt(UUID debtId) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));

        debt.cancel();
        Debt updated = debtRepository.save(debt);
        log.info("Cancelled debt {} for user {}", debtId, user.getId());
        return DebtDto.fromEntity(updated);
    }

    private User requireUser() {
        UUID userId = currentUser.require().getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }
}
