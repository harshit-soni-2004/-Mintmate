package com.spendsense.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsense.transaction.TransactionCategory;
import com.spendsense.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserMemoryPreferenceTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("ada@example.com", "hash", "Ada Lovelace", "Asia/Kolkata");
    }

    @Test
    @DisplayName("Empty memory returns Optional.empty")
    void emptyMemoryReturnsEmpty() {
        var pref = UserMemoryPreference.evaluate("AMAZON", Collections.emptyList());
        assertThat(pref).isEmpty();

        var prefNull = UserMemoryPreference.evaluate("AMAZON", null);
        assertThat(prefNull).isEmpty();

        var prefBlankMerchant = UserMemoryPreference.evaluate("   ", Collections.emptyList());
        assertThat(prefBlankMerchant).isEmpty();
    }

    @Test
    @DisplayName("Single observation is tentative and not an unquestionable permanent rule")
    void singleObservationIsTentative() {
        UserCategorizationMemory memory = new UserCategorizationMemory(user, "AMAZON", TransactionCategory.PERSONAL);

        var optPref = UserMemoryPreference.evaluate("AMAZON", List.of(memory));

        assertThat(optPref).isPresent();
        var pref = optPref.get();
        assertThat(pref.category()).isEqualTo(TransactionCategory.PERSONAL);
        assertThat(pref.totalUsageCount()).isEqualTo(1);
        assertThat(pref.categoryUsageCount()).isEqualTo(1);
        assertThat(pref.hasConflict()).isFalse();
        assertThat(pref.isStrongPreference()).isFalse();
        assertThat(pref.strengthScore()).isEqualTo(0.60);
        assertThat(pref.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
    }

    @Test
    @DisplayName("Repeated confirmations strengthen preference to HIGH confidence")
    void repeatedConfirmationsStrengthenPreference() {
        UserCategorizationMemory memory = new UserCategorizationMemory(user, "SWIGGY", TransactionCategory.FOOD);
        memory.recordUsage(); // usageCount = 2

        var optPref2 = UserMemoryPreference.evaluate("SWIGGY", List.of(memory));
        assertThat(optPref2).isPresent();
        assertThat(optPref2.get().isStrongPreference()).isTrue();
        assertThat(optPref2.get().confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(optPref2.get().strengthScore()).isEqualTo(0.85);

        memory.recordUsage(); // usageCount = 3
        var optPref3 = UserMemoryPreference.evaluate("SWIGGY", List.of(memory));
        assertThat(optPref3.get().strengthScore()).isEqualTo(0.92);
        assertThat(optPref3.get().isStrongPreference()).isTrue();

        memory.recordUsage(); // usageCount = 4
        var optPref4 = UserMemoryPreference.evaluate("SWIGGY", List.of(memory));
        assertThat(optPref4.get().strengthScore()).isEqualTo(0.98);
        assertThat(optPref4.get().isStrongPreference()).isTrue();
    }

    @Test
    @DisplayName("Conflicting history is detected and penalizes confidence strength")
    void conflictingHistoryReducesStrength() {
        UserCategorizationMemory shoppingMemory = new UserCategorizationMemory(user, "AMAZON", TransactionCategory.SHOPPING);
        shoppingMemory.recordUsage();
        shoppingMemory.recordUsage();
        shoppingMemory.recordUsage();
        shoppingMemory.recordUsage(); // usageCount = 5

        UserCategorizationMemory personalMemory = new UserCategorizationMemory(user, "AMAZON", TransactionCategory.PERSONAL);
        personalMemory.recordUsage(); // usageCount = 2

        var optPref = UserMemoryPreference.evaluate("AMAZON", List.of(shoppingMemory, personalMemory));

        assertThat(optPref).isPresent();
        var pref = optPref.get();
        assertThat(pref.category()).isEqualTo(TransactionCategory.SHOPPING);
        assertThat(pref.totalUsageCount()).isEqualTo(7);
        assertThat(pref.categoryUsageCount()).isEqualTo(5);
        assertThat(pref.hasConflict()).isTrue();
        // Ratio = 5/7 ~ 0.714 < 0.75 threshold for strong preference
        assertThat(pref.isStrongPreference()).isFalse();
        assertThat(pref.confidenceLevel()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(pref.strengthScore()).isBetween(0.65, 0.75);
    }

    @Test
    @DisplayName("Tie in conflicting memory breaks by most recent usage")
    void tieConflictBreaksByRecency() {
        UserCategorizationMemory travelMemory = new UserCategorizationMemory(user, "UBER", TransactionCategory.TRAVEL);

        // create food memory slightly later
        UserCategorizationMemory foodMemory = new UserCategorizationMemory(user, "UBER", TransactionCategory.FOOD);

        var optPref = UserMemoryPreference.evaluate("UBER", List.of(travelMemory, foodMemory));

        assertThat(optPref).isPresent();
        var pref = optPref.get();
        // foodMemory was instantiated later or equal
        assertThat(pref.hasConflict()).isTrue();
        assertThat(pref.isStrongPreference()).isFalse();
        assertThat(pref.totalUsageCount()).isEqualTo(2);
        assertThat(pref.confidenceLevel()).isEqualTo(ConfidenceLevel.LOW);
    }
}
