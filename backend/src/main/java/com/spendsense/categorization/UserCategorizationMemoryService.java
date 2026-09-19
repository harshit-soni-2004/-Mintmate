package com.spendsense.categorization;

import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserCategorizationMemoryService {

    private final UserCategorizationMemoryRepository memoryRepository;
    private final TransactionNormalizer normalizer;

    public UserCategorizationMemoryService(
            UserCategorizationMemoryRepository memoryRepository,
            TransactionNormalizer normalizer
    ) {
        this.memoryRepository = memoryRepository;
        this.normalizer = normalizer;
    }

    /**
     * Retrieves the evaluated preference for a specific user and merchant.
     * Applies canonical normalization so messy merchant variants converge to the same memory.
     */
    public Optional<UserMemoryPreference> getPreference(UUID userId, String merchant) {
        if (userId == null || merchant == null || merchant.isBlank()) {
            return Optional.empty();
        }
        String cleanMerchant = normalizer.normalizeMerchant(merchant);
        if ("unknown".equalsIgnoreCase(cleanMerchant)) {
            return Optional.empty();
        }
        List<UserCategorizationMemory> memories = memoryRepository.findAllByUserIdAndNormalizedMerchant(userId, cleanMerchant);
        return UserMemoryPreference.evaluate(cleanMerchant, memories);
    }

    /**
     * Records or reinforces a user's category preference for a merchant.
     * The merchant string is normalized deterministically and strictly scoped to the user.
     */
    @Transactional
    public UserCategorizationMemory recordPreference(User user, String merchant, TransactionCategory category) {
        Objects.requireNonNull(user, "User cannot be null");
        Objects.requireNonNull(category, "Category cannot be null");
        if (merchant == null || merchant.isBlank()) {
            throw new IllegalArgumentException("Merchant cannot be blank");
        }

        String cleanMerchant = normalizer.normalizeMerchant(merchant);
        if ("unknown".equalsIgnoreCase(cleanMerchant)) {
            throw new IllegalArgumentException("Merchant cannot be resolved from input: " + merchant);
        }

        return memoryRepository
                .findByUserIdAndNormalizedMerchantAndCategory(user.getId(), cleanMerchant, category)
                .map(existing -> {
                    existing.recordUsage();
                    return memoryRepository.save(existing);
                })
                .orElseGet(() -> memoryRepository.save(new UserCategorizationMemory(user, cleanMerchant, category)));
    }

    /**
     * Returns all historical category associations for a user and merchant.
     */
    public List<UserCategorizationMemory> getMemoriesForMerchant(UUID userId, String merchant) {
        if (userId == null || merchant == null || merchant.isBlank()) {
            return Collections.emptyList();
        }
        String cleanMerchant = normalizer.normalizeMerchant(merchant);
        return memoryRepository.findAllByUserIdAndNormalizedMerchant(userId, cleanMerchant);
    }
}
