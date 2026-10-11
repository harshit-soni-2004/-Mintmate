package com.spendsense.debt;

import com.spendsense.common.exception.ApiException;
import com.spendsense.debt.dto.CreateDebtRequest;
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
import java.util.List;
import java.util.UUID;

@Service
public class DebtService {

    private static final Logger log = LoggerFactory.getLogger(DebtService.class);

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
            return DebtDto.fromEntity(debt);
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

        debt.settle(settlementTx);
        Debt updated = debtRepository.save(debt);
        log.info("Settled debt {} for user {}, linked tx: {}", debtId, user.getId(),
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
