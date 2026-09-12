package com.spendsense.analytics;

import java.math.BigDecimal;

public final class AnalyticsThresholds {

    private AnalyticsThresholds() {}

    public static final double CATEGORY_CHANGE_PERCENT = 20.0;
    public static final BigDecimal MIN_CATEGORY_CHANGE_AMOUNT = BigDecimal.valueOf(500.00);

    public static final double SAVINGS_RATE_CHANGE_POINTS = 5.0;

    public static final double LARGE_TRANSACTION_MULTIPLIER = 3.0;
    public static final BigDecimal MIN_LARGE_TRANSACTION_AMOUNT = BigDecimal.valueOf(1000.00);
    public static final double CATEGORY_SPIKE_PERCENT = 50.0;
    public static final BigDecimal MIN_CATEGORY_SPIKE_AMOUNT = BigDecimal.valueOf(1000.00);
    public static final double PERIOD_SPIKE_PERCENT = 30.0;
    public static final BigDecimal MIN_PERIOD_SPIKE_AMOUNT = BigDecimal.valueOf(2000.00);

    public static final double BUDGET_WARNING_THRESHOLD = 75.0;
    public static final double BUDGET_EXCEEDED_THRESHOLD = 100.0;
}
