package com.spendsense.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserCategorizationMemoryServiceTest {

    @Mock
    private UserCategorizationMemoryRepository memoryRepository;

    private UserCategorizationMemoryService service;
    private TransactionNormalizer normalizer;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
        service = new UserCategorizationMemoryService(memoryRepository, normalizer);
        userA = new User("usera@example.com", "hash", "User A", "Asia/Kolkata");
        userB = new User("userb@example.com", "hash", "User B", "Asia/Kolkata");
    }

    @Test
    @DisplayName("Strict user isolation: User A preference is isolated from User B")
    void strictUserIsolation() {
        UserCategorizationMemory memoryA = new UserCategorizationMemory(userA, "amazon", TransactionCategory.PERSONAL);
        UserCategorizationMemory memoryB = new UserCategorizationMemory(userB, "amazon", TransactionCategory.SHOPPING);

        when(memoryRepository.findAllByUserIdAndNormalizedMerchant(userA.getId(), "amazon"))
                .thenReturn(List.of(memoryA));
        when(memoryRepository.findAllByUserIdAndNormalizedMerchant(userB.getId(), "amazon"))
                .thenReturn(List.of(memoryB));

        UUID userCId = UUID.randomUUID();
        when(memoryRepository.findAllByUserIdAndNormalizedMerchant(userCId, "amazon"))
                .thenReturn(Collections.emptyList());

        var prefA = service.getPreference(userA.getId(), "AMAZON");
        var prefB = service.getPreference(userB.getId(), "AMAZON");
        var prefC = service.getPreference(userCId, "AMAZON");

        assertThat(prefA).isPresent();
        assertThat(prefA.get().category()).isEqualTo(TransactionCategory.PERSONAL);

        assertThat(prefB).isPresent();
        assertThat(prefB.get().category()).isEqualTo(TransactionCategory.SHOPPING);

        assertThat(prefC).isEmpty();
    }

    @Test
    @DisplayName("First-time recording creates new memory with normalized merchant and usage count 1")
    void firstTimeRecordCreatesNewMemory() {
        when(memoryRepository.findByUserIdAndNormalizedMerchantAndCategory(userA.getId(), "starbucks", TransactionCategory.FOOD))
                .thenReturn(Optional.empty());
        when(memoryRepository.save(any(UserCategorizationMemory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserCategorizationMemory saved = service.recordPreference(userA, "  Starbucks  ", TransactionCategory.FOOD);

        assertThat(saved.getNormalizedMerchant()).isEqualTo("starbucks");
        assertThat(saved.getCategory()).isEqualTo(TransactionCategory.FOOD);
        assertThat(saved.getUsageCount()).isEqualTo(1);
        assertThat(saved.getUser().getId()).isEqualTo(userA.getId());
    }

    @Test
    @DisplayName("Subsequent recording increments existing memory usage count")
    void subsequentRecordingIncrementsUsageCount() {
        UserCategorizationMemory existing = new UserCategorizationMemory(userA, "starbucks", TransactionCategory.FOOD);
        assertThat(existing.getUsageCount()).isEqualTo(1);

        when(memoryRepository.findByUserIdAndNormalizedMerchantAndCategory(userA.getId(), "starbucks", TransactionCategory.FOOD))
                .thenReturn(Optional.of(existing));
        when(memoryRepository.save(any(UserCategorizationMemory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserCategorizationMemory updated = service.recordPreference(userA, "starbucks", TransactionCategory.FOOD);

        assertThat(updated.getUsageCount()).isEqualTo(2);
        verify(memoryRepository).save(existing);
    }

    @Test
    @DisplayName("User memory integration: differently formatted Swiggy transactions find same memory")
    void messyVariantsResolveToSameMemory() {
        UserCategorizationMemory swiggyMemory = new UserCategorizationMemory(userA, "swiggy", TransactionCategory.FOOD);
        swiggyMemory.recordUsage(); // usage count 2

        when(memoryRepository.findAllByUserIdAndNormalizedMerchant(userA.getId(), "swiggy"))
                .thenReturn(List.of(swiggyMemory));

        // Query with UPI format
        var prefUpi = service.getPreference(userA.getId(), "UPI-SWIGGY-834723");
        assertThat(prefUpi).isPresent();
        assertThat(prefUpi.get().category()).isEqualTo(TransactionCategory.FOOD);
        assertThat(prefUpi.get().normalizedMerchant()).isEqualTo("swiggy");

        // Query with Order and delimiters
        var prefOrder = service.getPreference(userA.getId(), "SWIGGY*ORDER*12345");
        assertThat(prefOrder).isPresent();
        assertThat(prefOrder.get().category()).isEqualTo(TransactionCategory.FOOD);

        // Query with card ISO code
        var prefIn = service.getPreference(userA.getId(), "SWIGGY IN");
        assertThat(prefIn).isPresent();
        assertThat(prefIn.get().category()).isEqualTo(TransactionCategory.FOOD);

        // Distinct merchant does not match Swiggy memory
        when(memoryRepository.findAllByUserIdAndNormalizedMerchant(userA.getId(), "zomato"))
                .thenReturn(Collections.emptyList());
        var prefZomato = service.getPreference(userA.getId(), "UPI-ZOMATO-998822");
        assertThat(prefZomato).isEmpty();
    }

    @Test
    @DisplayName("Throws exception on invalid input")
    void throwsOnInvalidInput() {
        assertThatThrownBy(() -> service.recordPreference(null, "SWIGGY", TransactionCategory.FOOD))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> service.recordPreference(userA, "   ", TransactionCategory.FOOD))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> service.recordPreference(userA, "SWIGGY", null))
                .isInstanceOf(NullPointerException.class);
    }
}
