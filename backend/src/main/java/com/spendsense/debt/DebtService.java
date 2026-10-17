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

    public DebtService(
            DebtRepository debtRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CurrentUser currentUser
    ) {
        this.debtRepository = debtRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
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
     * - Ranked by exact amount + counterparty > exact amount > close amount > recent date
     */
    @Transactional(readOnly = true)
    public List<DebtCandidateTransactionDto> getCandidateTransactions(UUID debtId) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));

        LocalDate refDate = debt.getCreatedAt() != null
                ? debt.getCreatedAt().atZone(IST_ZONE).toLocalDate()
                : LocalDate.now(IST_ZONE);
        LocalDate earliestDate = refDate.minusDays(CREATED_AT_LOOKBACK_DAYS);

        List<UUID> linkedIdsList = debtRepository.findLinkedTransactionIdsByUserId(user.getId());
        Set<UUID> linkedIds = new HashSet<>(linkedIdsList != null ? linkedIdsList : List.of());
        if (debt.getLinkedTransaction() != null) {
            linkedIds.remove(debt.getLinkedTransaction().getId());
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
     * Settles a debt entry.
     * When linked to a transaction, reconciles without double-counting:
     * - If OWED_TO_ME: Repayment credit is excluded from Income metrics (reclassified as refund/settlement)
     * - If I_OWE: Repayment debit is reclassified to prevent inflating discretionary category budgets
     */
    @Transactional
    public DebtDto settleDebt(UUID debtId, SettleDebtRequest settleRequest) {
        User user = requireUser();
        Debt debt = debtRepository.findByIdAndUserId(debtId, user.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEBT_NOT_FOUND", "Debt entry not found"));

        if (debt.getStatus() == DebtStatus.SETTLED) {
            if (debt.getLinkedTransaction() != null || settleRequest == null || settleRequest.linkedTransactionId() == null) {
                return DebtDto.fromEntity(debt);
            }
            // Allow linking a settlement transaction to a debt that was previously settled without a link
        }

        Transaction settlementTx = null;
        if (settleRequest != null && settleRequest.linkedTransactionId() != null) {
            settlementTx = transactionRepository.findById(settleRequest.linkedTransactionId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Settlement transaction not found"));

            if (!settlementTx.getUser().getId().equals(user.getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Cannot link transaction belonging to another user");
            }

            // Reconcile transaction to avoid double counting
            if (debt.getDebtType() == DebtType.OWED_TO_ME) {
                // Incoming repayment is not salary/income: tag as Tier 3 refund/repayment
                settlementTx.setIncomeTier(IncomeTier.TIER_3_REFUND);
                settlementTx.setCategory(TransactionCategory.OTHER);
                // Also set transfer direction as IN if transferred
                if (settlementTx.getTransactionType() == TransactionType.INCOME) {
                    settlementTx.setTransactionType(TransactionType.TRANSFER);
                    settlementTx.setTransferDirection(TransferDirection.IN);
                }
            } else {
                // Outgoing repayment is debt settlement: assign OTHER category to protect category budgets
                settlementTx.setCategory(TransactionCategory.OTHER);
                if (settlementTx.getTransactionType() == TransactionType.EXPENSE) {
                    settlementTx.setTransactionType(TransactionType.TRANSFER);
                    settlementTx.setTransferDirection(TransferDirection.OUT);
                }
            }
            transactionRepository.save(settlementTx);
        }

        if (debt.getStatus() != DebtStatus.SETTLED) {
            debt.settle(settlementTx);
        } else {
            debt.setLinkedTransaction(settlementTx);
        }
        Debt updated = debtRepository.save(debt);
        log.info("Settled/linked debt {} for user {}, linked tx: {}", debtId, user.getId(),
                settlementTx != null ? settlementTx.getId() : "none");
        return DebtDto.fromEntity(updated);
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
