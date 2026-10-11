package com.spendsense.debt.dto;

import java.math.BigDecimal;
import java.util.List;

public record DebtSummaryDto(
        BigDecimal totalReceivable,  // Money others owe to the user
        BigDecimal totalPayable,     // Money the user owes to others
        BigDecimal netDebtPosition,  // totalReceivable - totalPayable
        int activeCount,
        int overdueCount,
        List<DebtDto> upcomingDebts,
        List<DebtDto> overdueDebts
) {}
