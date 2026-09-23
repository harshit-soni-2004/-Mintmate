package com.spendsense.account;

import com.spendsense.account.dto.FinancialAccountRequest;
import com.spendsense.account.dto.FinancialAccountResponse;
import com.spendsense.common.exception.ApiException;
import com.spendsense.document.DocumentAccountType;
import com.spendsense.security.CurrentUser;
import com.spendsense.user.User;
import com.spendsense.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FinancialAccountService {

    private final FinancialAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public FinancialAccountService(
            FinancialAccountRepository accountRepository,
            UserRepository userRepository,
            CurrentUser currentUser
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public FinancialAccountResponse create(FinancialAccountRequest request) {
        Objects.requireNonNull(request, "Account request cannot be null");
        User user = requireUser();

        FinancialAccount account = new FinancialAccount(
                user,
                request.name(),
                request.institutionName(),
                request.accountType(),
                request.accountNumberLast4(),
                request.currency(),
                Boolean.TRUE.equals(request.primary())
        );

        FinancialAccount saved = accountRepository.save(account);
        return FinancialAccountResponse.from(saved);
    }

    public FinancialAccountResponse get(UUID id) {
        User user = requireUser();
        FinancialAccount account = findOwned(id, user.getId());
        return FinancialAccountResponse.from(account);
    }

    public List<FinancialAccountResponse> list() {
        User user = requireUser();
        return accountRepository.findByUserIdAndActiveTrueOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(FinancialAccountResponse::from)
                .toList();
    }

    @Transactional
    public FinancialAccountResponse update(UUID id, FinancialAccountRequest request) {
        User user = requireUser();
        FinancialAccount account = findOwned(id, user.getId());
        account.update(
                request.name(),
                request.institutionName(),
                request.accountType(),
                request.accountNumberLast4(),
                Boolean.TRUE.equals(request.primary())
        );
        FinancialAccount saved = accountRepository.save(account);
        return FinancialAccountResponse.from(saved);
    }

    @Transactional
    public void deactivate(UUID id) {
        User user = requireUser();
        FinancialAccount account = findOwned(id, user.getId());
        account.deactivate();
        accountRepository.save(account);
    }

    @Transactional
    public FinancialAccount getOrCreateForStatement(User user, String institutionName, DocumentAccountType accountType, String last4) {
        String inst = (institutionName != null && !institutionName.isBlank()) ? institutionName.trim() : null;
        DocumentAccountType type = accountType != null ? accountType : DocumentAccountType.SAVINGS;
        String l4 = (last4 != null && !last4.isBlank()) ? last4.trim() : null;

        // When neither institution name nor account last4 is provided, account identity is ambiguous.
        // Prevent unrelated statements from silently collapsing into a shared generic account.
        if (inst == null && l4 == null) {
            String name = "Unspecified (" + type + ")";
            FinancialAccount created = new FinancialAccount(user, name, "Unknown Bank", type, null, "INR", false);
            return accountRepository.save(created);
        }

        String effectiveInst = inst != null ? inst : "Bank";
        return accountRepository.findMatchingAccount(user.getId(), effectiveInst, type, l4)
                .orElseGet(() -> {
                    String name = effectiveInst + " (" + type + (l4 != null ? " - " + l4 : "") + ")";
                    FinancialAccount created = new FinancialAccount(user, name, effectiveInst, type, l4, "INR", false);
                    return accountRepository.save(created);
                });
    }

    public FinancialAccount findOwned(UUID id, UUID userId) {
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Financial account not found"));
    }

    private User requireUser() {
        UUID userId = currentUser.require().getUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"));
    }
}
