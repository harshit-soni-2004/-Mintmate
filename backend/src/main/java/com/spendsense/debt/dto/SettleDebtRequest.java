package com.spendsense.debt.dto;

import java.util.UUID;

public record SettleDebtRequest(
        UUID linkedTransactionId
) {}
