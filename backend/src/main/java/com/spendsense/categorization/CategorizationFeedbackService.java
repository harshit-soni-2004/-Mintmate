package com.spendsense.categorization;

import com.spendsense.user.User;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategorizationFeedbackService {

    private final CategorizationFeedbackRepository feedbackRepository;
    private final UserCategorizationMemoryService userMemoryService;
    private final TransactionNormalizer normalizer;

    public CategorizationFeedbackService(
            CategorizationFeedbackRepository feedbackRepository,
            UserCategorizationMemoryService userMemoryService,
            TransactionNormalizer normalizer
    ) {
        this.feedbackRepository = feedbackRepository;
        this.userMemoryService = userMemoryService;
        this.normalizer = normalizer;
    }

    /**
     * Records categorization feedback for offline training and, optionally, updates
     * the user's personal categorization memory.
     * Note: Model retraining is NEVER executed synchronously.
     */
    @Transactional
    public CategorizationFeedback recordFeedback(CategorizationFeedbackCommand command) {
        Objects.requireNonNull(command, "Feedback command cannot be null");
        Objects.requireNonNull(command.user(), "User cannot be null");
        Objects.requireNonNull(command.finalCategory(), "Final category cannot be null");

        String normalizedMerchant = command.normalizedMerchant();
        if (normalizedMerchant == null || normalizedMerchant.isBlank()) {
            if (command.rawMerchant() != null && !command.rawMerchant().isBlank()) {
                normalizedMerchant = normalizer.normalizeMerchant(command.rawMerchant());
            } else {
                normalizedMerchant = "unknown";
            }
        } else {
            normalizedMerchant = normalizer.normalizeMerchant(normalizedMerchant);
        }

        CategorizationFeedback feedback = new CategorizationFeedback(
                command.user(),
                command.transaction(),
                command.rawMerchant(),
                normalizedMerchant,
                command.rawDescription(),
                command.amount(),
                command.predictedCategory(),
                command.confidence(),
                command.confidenceLevel(),
                command.finalCategory(),
                command.predictionSource() != null ? command.predictionSource() : CategorizationSource.MANUAL,
                command.modelVersion()
        );

        CategorizationFeedback savedFeedback = feedbackRepository.save(feedback);

        if (command.updateMemory() && !"unknown".equalsIgnoreCase(normalizedMerchant)) {
            userMemoryService.recordPreference(command.user(), normalizedMerchant, command.finalCategory());
        }

        return savedFeedback;
    }

    public Page<CategorizationFeedback> getFeedbackHistory(UUID userId, Pageable pageable) {
        Objects.requireNonNull(userId, "User ID cannot be null");
        return feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public long countFeedback(UUID userId) {
        if (userId == null) {
            return 0;
        }
        return feedbackRepository.countByUserId(userId);
    }
}
