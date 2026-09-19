package com.spendsense.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategorizationFeedbackServiceTest {

    @Mock
    private CategorizationFeedbackRepository feedbackRepository;

    @Mock
    private UserCategorizationMemoryService userMemoryService;

    private CategorizationFeedbackService feedbackService;
    private TransactionNormalizer normalizer;
    private User user;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
        feedbackService = new CategorizationFeedbackService(feedbackRepository, userMemoryService, normalizer);
        user = new User("ada@example.com", "hash", "Ada Lovelace", "Asia/Kolkata");
    }

    @Test
    @DisplayName("Records corrected prediction and triggers memory update with normalized merchant")
    void recordsCorrectedPredictionAndUpdatesMemory() {
        when(feedbackRepository.save(any(CategorizationFeedback.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var cmd = CategorizationFeedbackCommand.builder()
                .user(user)
                .rawMerchant("SWIGGY*ORDER*123")
                .normalizedMerchant("SWIGGY")
                .rawDescription("Late night biryani")
                .amount(new BigDecimal("450.00"))
                .predictedCategory(TransactionCategory.SHOPPING)
                .confidence(new BigDecimal("0.7200"))
                .confidenceLevel(ConfidenceLevel.MEDIUM)
                .finalCategory(TransactionCategory.FOOD)
                .predictionSource(CategorizationSource.ML)
                .modelVersion("v1.0.0-baseline")
                .updateMemory(true)
                .build();

        CategorizationFeedback saved = feedbackService.recordFeedback(cmd);

        assertThat(saved.isCorrected()).isTrue();
        assertThat(saved.getPredictedCategory()).isEqualTo(TransactionCategory.SHOPPING);
        assertThat(saved.getFinalCategory()).isEqualTo(TransactionCategory.FOOD);
        assertThat(saved.getNormalizedMerchant()).isEqualTo("swiggy");
        assertThat(saved.getPredictionSource()).isEqualTo(CategorizationSource.ML);
        assertThat(saved.getModelVersion()).isEqualTo("v1.0.0-baseline");

        verify(feedbackRepository).save(any(CategorizationFeedback.class));
        verify(userMemoryService).recordPreference(user, "swiggy", TransactionCategory.FOOD);
    }

    @Test
    @DisplayName("Records accepted (uncorrected) prediction and reinforces user memory")
    void recordsAcceptedPrediction() {
        when(feedbackRepository.save(any(CategorizationFeedback.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var cmd = CategorizationFeedbackCommand.builder()
                .user(user)
                .rawMerchant("NETFLIX.COM")
                .normalizedMerchant("NETFLIX")
                .predictedCategory(TransactionCategory.ENTERTAINMENT)
                .confidence(new BigDecimal("0.9600"))
                .confidenceLevel(ConfidenceLevel.HIGH)
                .finalCategory(TransactionCategory.ENTERTAINMENT)
                .predictionSource(CategorizationSource.ML)
                .build();

        CategorizationFeedback saved = feedbackService.recordFeedback(cmd);

        assertThat(saved.isCorrected()).isFalse();
        assertThat(saved.getPredictedCategory()).isEqualTo(TransactionCategory.ENTERTAINMENT);
        assertThat(saved.getFinalCategory()).isEqualTo(TransactionCategory.ENTERTAINMENT);
        assertThat(saved.getNormalizedMerchant()).isEqualTo("netflix");

        verify(userMemoryService).recordPreference(user, "netflix", TransactionCategory.ENTERTAINMENT);
    }

    @Test
    @DisplayName("Skips user memory update when updateMemory is false")
    void skipsMemoryUpdateWhenRequested() {
        when(feedbackRepository.save(any(CategorizationFeedback.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var cmd = CategorizationFeedbackCommand.builder()
                .user(user)
                .rawMerchant("UNKNOWN VENDOR")
                .finalCategory(TransactionCategory.OTHER)
                .updateMemory(false)
                .build();

        CategorizationFeedback saved = feedbackService.recordFeedback(cmd);

        assertThat(saved).isNotNull();
        verify(userMemoryService, never()).recordPreference(any(), any(), any());
    }

    @Test
    @DisplayName("Throws exception when user or final category is missing")
    void validatesRequiredFields() {
        var missingUser = CategorizationFeedbackCommand.builder()
                .finalCategory(TransactionCategory.FOOD)
                .build();

        assertThatThrownBy(() -> feedbackService.recordFeedback(missingUser))
                .isInstanceOf(NullPointerException.class);

        var missingFinalCategory = CategorizationFeedbackCommand.builder()
                .user(user)
                .build();

        assertThatThrownBy(() -> feedbackService.recordFeedback(missingFinalCategory))
                .isInstanceOf(NullPointerException.class);
    }
}
