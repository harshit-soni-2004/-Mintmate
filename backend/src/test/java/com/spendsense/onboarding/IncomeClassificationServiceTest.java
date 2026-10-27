package com.spendsense.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.spendsense.ingestion.SourceTransactionClassification;
import com.spendsense.transaction.IncomeTier;
import com.spendsense.transaction.Transaction;
import com.spendsense.transaction.TransactionCategory;
import com.spendsense.transaction.TransactionRepository;
import com.spendsense.transaction.TransactionType;
import com.spendsense.user.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncomeClassificationServiceTest {

    @Mock private UserFinancialProfileRepository profileRepository;
    @Mock private UserIncomeSourceRepository incomeSourceRepository;
    @Mock private TransactionRepository transactionRepository;

    private IncomeClassificationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new IncomeClassificationService(profileRepository, incomeSourceRepository, transactionRepository);
        user = new User("harshit@example.com", "hash", "Harshit", "Asia/Kolkata");
    }

    @Test
    @DisplayName("Tier 1: Regular salary credit matching declared employer, amount, and date window is verified")
    void testSalariedMatchVerified() {
        UserFinancialProfile profile = new UserFinancialProfile(user, UserPersona.SALARIED, new BigDecimal("25000"), new BigDecimal("15000"));
        UserIncomeSource source = new UserIncomeSource(
                user, "Acme Tech", IncomeType.SALARY, IncomeFrequency.MONTHLY,
                new BigDecimal("80000.00"), 30, "ACME", true, false
        );

        when(profileRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(profile));
        when(incomeSourceRepository.findByUser_Id(user.getId())).thenReturn(List.of(source));

        LocalDate creditDate = LocalDate.of(2026, 9, 30);
        IncomeClassificationResult result = service.classifyCredit(
                user, new BigDecimal("80000.00"), creditDate,
                "ACH CR/ACME TECH CORP/SALARY FOR SEP", "Acme Tech", null
        );

        assertThat(result.tier()).isEqualTo(IncomeTier.TIER_1_SALARY);
        assertThat(result.isIncome()).isTrue();
        assertThat(result.isVerified()).isTrue();
        assertThat(result.requiresReview()).isFalse();
        assertThat(result.divergencePercentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Tier 1 Divergence: Salary credit diverging by > 10% flags user review without rejecting salary")
    void testSalaryCreditDivergenceAlert() {
        UserFinancialProfile profile = new UserFinancialProfile(user, UserPersona.SALARIED, new BigDecimal("25000"), new BigDecimal("15000"));
        UserIncomeSource source = new UserIncomeSource(
                user, "Acme Tech", IncomeType.SALARY, IncomeFrequency.MONTHLY,
                new BigDecimal("80000.00"), 30, "ACME", true, false
        );

        when(profileRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(profile));
        when(incomeSourceRepository.findByUser_Id(user.getId())).thenReturn(List.of(source));

        // Credit is 105,000 (e.g. bonus or appraisal) -> divergence is 31.25%
        LocalDate creditDate = LocalDate.of(2026, 9, 30);
        IncomeClassificationResult result = service.classifyCredit(
                user, new BigDecimal("105000.00"), creditDate,
                "CMS/ACME TECH/PAYROLL SALARY", "Acme Tech", null
        );

        assertThat(result.tier()).isEqualTo(IncomeTier.TIER_1_SALARY);
        assertThat(result.isIncome()).isTrue();
        assertThat(result.isVerified()).isFalse();
        assertThat(result.requiresReview()).isTrue();
        assertThat(result.reviewReason()).contains("diverges from declared ₹80000.00 by 31.3%");
    }

    @Test
    @DisplayName("Missing Salary Credit: Flags alert when date is > 5 days past expected salary day and no credit detected")
    void testMissingSalaryCreditDetection() {
        UserIncomeSource source = new UserIncomeSource(
                user, "Primary Salary", IncomeType.SALARY, IncomeFrequency.MONTHLY,
                new BigDecimal("80000.00"), 1, "ACME", true, false
        );

        when(incomeSourceRepository.findByUser_Id(user.getId())).thenReturn(List.of(source));
        // Current date is 7th (6 days past 1st)
        LocalDate currentDate = LocalDate.of(2026, 10, 7);
        when(transactionRepository.findByUser_IdAndTransactionTypeAndTransactionDateBetweenOrderByAmountDesc(
                eq(user.getId()), eq(TransactionType.INCOME), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of()); // No income transactions found in October

        Optional<String> alert = service.checkMissingSalaryCredit(user.getId(), currentDate);

        assertThat(alert).isPresent();
        assertThat(alert.get()).contains("Expected salary credit of ₹80000.00 around day 1 has not been detected");
    }

    @Test
    @DisplayName("Tier 2: Freelancer / Business owner deposits are classified as Variable Professional Income")
    void testVariableFreelanceIncome() {
        UserFinancialProfile profile = new UserFinancialProfile(user, UserPersona.FREELANCER, null, null);
        UserIncomeSource source = new UserIncomeSource(
                user, "Design Invoices", IncomeType.FREELANCE, IncomeFrequency.IRREGULAR,
                new BigDecimal("50000.00"), null, null, true, true
        );

        when(profileRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(profile));
        when(incomeSourceRepository.findByUser_Id(user.getId())).thenReturn(List.of(source));

        LocalDate creditDate = LocalDate.of(2026, 9, 15);
        IncomeClassificationResult result = service.classifyCredit(
                user, new BigDecimal("45000.00"), creditDate,
                "NEFT CR-CLIENT STUDIOS PVT LTD", "Client Studios", null
        );

        assertThat(result.tier()).isEqualTo(IncomeTier.TIER_2_VARIABLE);
        assertThat(result.isIncome()).isTrue();
        assertThat(result.isVerified()).isFalse(); // variable income is an estimate/run-rate
    }

    @Test
    @DisplayName("Tier 3: Refunds and reversals are classified as Tier 3 and excluded from income")
    void testRefundsExcludedFromIncome() {
        IncomeClassificationResult result = service.classifyCredit(
                user, new BigDecimal("2499.00"), LocalDate.of(2026, 9, 12),
                "AMAZON REFUND RET-983141", "Amazon", SourceTransactionClassification.REFUND
        );

        assertThat(result.tier()).isEqualTo(IncomeTier.TIER_3_REFUND);
        assertThat(result.isIncome()).isFalse();
    }

    @Test
    @DisplayName("Tier 4: Ad-hoc P2P peer transfers (friend repayments) are quarantined from Income")
    void testPeerRepaymentExcludedFromIncome() {
        UserFinancialProfile profile = new UserFinancialProfile(user, UserPersona.SALARIED, null, null);
        when(profileRepository.findByUser_Id(user.getId())).thenReturn(Optional.of(profile));
        when(incomeSourceRepository.findByUser_Id(user.getId())).thenReturn(List.of());

        IncomeClassificationResult result = service.classifyCredit(
                user, new BigDecimal("1250.00"), LocalDate.of(2026, 9, 14),
                "UPI/CR/658828783145/ROHIT/DINNER SHARE", "Rohit", null
        );

        assertThat(result.tier()).isEqualTo(IncomeTier.TIER_4_OTHER_CREDIT);
        assertThat(result.isIncome()).isFalse();
        assertThat(result.reviewReason()).contains("excluded from income metric");
    }

    @Test
    @DisplayName("Date window matching handles end-of-month / beginning-of-month boundaries safely")
    void testDateWindowNearMonthBoundary() {
        // Expected day 1st, credited on 29th of previous month (2 days early)
        assertThat(service.isDateWithinExpectedWindow(1, LocalDate.of(2026, 8, 30))).isTrue();
        // Expected day 31st, credited on 2nd of next month (2 days late)
        assertThat(service.isDateWithinExpectedWindow(31, LocalDate.of(2026, 9, 2))).isTrue();
        // Expected day 1st, credited on 15th (14 days away) -> False
        assertThat(service.isDateWithinExpectedWindow(1, LocalDate.of(2026, 9, 15))).isFalse();
    }
}
