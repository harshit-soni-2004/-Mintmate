package com.spendsense.categorization.rule;

import com.spendsense.categorization.ConfidenceLevel;
import com.spendsense.categorization.NormalizedTransaction;
import com.spendsense.categorization.TransactionNormalizer;
import com.spendsense.transaction.TransactionCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Categorization Rule Engine Tests")
class RuleEngineTest {

    private TransactionNormalizer normalizer;
    private RuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        normalizer = new TransactionNormalizer();
        ruleEngine = new RuleEngine(List.of(
                new PayrollAndIncomeRule(),
                new TaxAndGovernmentRule(),
                new BankInterestAndDividendRule(),
                new AtmCashWithdrawalRule(),
                new InternalTransferRule()
        ));
    }

    private RuleContext buildContext(String merchant, String description) {
        NormalizedTransaction norm = normalizer.normalize(merchant, description);
        return new RuleContext(
                UUID.randomUUID(),
                norm,
                new BigDecimal("100.00"),
                "ACH",
                LocalDate.now()
        );
    }

    @Test
    @DisplayName("Should detect payroll and direct deposit credits deterministically")
    void shouldTriggerPayrollRule() {
        RuleContext context = buildContext("EMPLOYER ACME INC", "PAYROLL DIRECT DEP 09/15");
        Optional<RuleMatchResult> result = ruleEngine.evaluate(context);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("RULE_PAYROLL_INCOME");
        assertThat(result.get().category()).isEqualTo(TransactionCategory.OTHER);
        assertThat(result.get().confidenceLevel()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.get().confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should detect tax and government revenue payments deterministically")
    void shouldTriggerTaxRule() {
        RuleContext context = buildContext("IRS USATAX", "PAYMENT ELECTRONIC 1040");
        Optional<RuleMatchResult> result = ruleEngine.evaluate(context);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("RULE_TAX_GOVERNMENT");
        assertThat(result.get().category()).isEqualTo(TransactionCategory.BILLS);
    }

    @Test
    @DisplayName("Should detect bank interest credits deterministically")
    void shouldTriggerBankInterestRule() {
        RuleContext context = buildContext("CHASE BANK", "INTEREST PAYMENT DEPOSIT");
        Optional<RuleMatchResult> result = ruleEngine.evaluate(context);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("RULE_BANK_INTEREST_DIVIDEND");
        assertThat(result.get().category()).isEqualTo(TransactionCategory.OTHER);
    }

    @Test
    @DisplayName("Should detect ATM cash withdrawals deterministically")
    void shouldTriggerAtmCashRule() {
        RuleContext context = buildContext("ATM", "WDL CHK #9948 DOWNTOWN");
        Optional<RuleMatchResult> result = ruleEngine.evaluate(context);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("RULE_ATM_CASH_WITHDRAWAL");
        assertThat(result.get().category()).isEqualTo(TransactionCategory.OTHER);
    }

    @Test
    @DisplayName("Should detect internal account transfers deterministically")
    void shouldTriggerInternalTransferRule() {
        RuleContext context = buildContext("BANK OF AMERICA", "TRANSFER TO SAVINGS 4821");
        Optional<RuleMatchResult> result = ruleEngine.evaluate(context);

        assertThat(result).isPresent();
        assertThat(result.get().ruleId()).isEqualTo("RULE_INTERNAL_TRANSFER");
        assertThat(result.get().category()).isEqualTo(TransactionCategory.OTHER);
    }

    @Test
    @DisplayName("Should return empty when no deterministic rule matches")
    void shouldReturnEmptyForNormalExpense() {
        RuleContext context = buildContext("STARBUCKS", "COFFEE STORE #123");
        Optional<RuleMatchResult> result = ruleEngine.evaluate(context);

        assertThat(result).isEmpty();
    }
}
