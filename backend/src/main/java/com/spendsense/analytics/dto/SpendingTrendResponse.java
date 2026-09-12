package com.spendsense.analytics.dto;

import com.spendsense.analytics.AggregationPeriod;
import java.time.LocalDate;
import java.util.List;

public record SpendingTrendResponse(
        AggregationPeriod period,
        List<SpendingTrendPoint> points,
        LocalDate fromDate,
        LocalDate toDate
) {}
