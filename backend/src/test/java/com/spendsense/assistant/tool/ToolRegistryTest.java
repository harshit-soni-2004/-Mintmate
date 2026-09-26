package com.spendsense.assistant.tool;

import com.spendsense.assistant.tool.impl.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    @Test
    @DisplayName("ToolRegistry should contain exactly the six registered read-only tools")
    void shouldRegisterAllSixReadOnlyTools() {
        GetSpendingSummaryTool t1 = Mockito.mock(GetSpendingSummaryTool.class);
        Mockito.when(t1.getName()).thenReturn(GetSpendingSummaryTool.NAME);

        GetSpendingByCategoryTool t2 = Mockito.mock(GetSpendingByCategoryTool.class);
        Mockito.when(t2.getName()).thenReturn(GetSpendingByCategoryTool.NAME);

        SearchTransactionsTool t3 = Mockito.mock(SearchTransactionsTool.class);
        Mockito.when(t3.getName()).thenReturn(SearchTransactionsTool.NAME);

        GetBudgetStatusTool t4 = Mockito.mock(GetBudgetStatusTool.class);
        Mockito.when(t4.getName()).thenReturn(GetBudgetStatusTool.NAME);

        GetFinancialAccountsTool t5 = Mockito.mock(GetFinancialAccountsTool.class);
        Mockito.when(t5.getName()).thenReturn(GetFinancialAccountsTool.NAME);

        CompareSpendingPeriodsTool t6 = Mockito.mock(CompareSpendingPeriodsTool.class);
        Mockito.when(t6.getName()).thenReturn(CompareSpendingPeriodsTool.NAME);

        ToolRegistry registry = new ToolRegistry(List.of(t1, t2, t3, t4, t5, t6));

        assertThat(registry.getAllToolDefinitions()).hasSize(6);
        assertThat(registry.hasTool("get_spending_summary")).isTrue();
        assertThat(registry.hasTool("get_spending_by_category")).isTrue();
        assertThat(registry.hasTool("search_transactions")).isTrue();
        assertThat(registry.hasTool("get_budget_status")).isTrue();
        assertThat(registry.hasTool("get_financial_accounts")).isTrue();
        assertThat(registry.hasTool("compare_spending_periods")).isTrue();

        Optional<FinancialTool<?, ?>> unknown = registry.getTool("unknown_arbitrary_tool");
        assertThat(unknown).isEmpty();
        assertThat(registry.hasTool("unknown_arbitrary_tool")).isFalse();
    }
}
