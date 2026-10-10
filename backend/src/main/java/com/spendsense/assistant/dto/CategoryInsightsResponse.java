package com.spendsense.assistant.dto;

import com.spendsense.assistant.context.model.CategoryProfile;
import com.spendsense.assistant.context.model.DataSufficiency;

import java.util.List;

public record CategoryInsightsResponse(
        String highestSpendCategory,
        String mostVolatileCategory,
        String fastestGrowingCategory,
        List<CategoryProfile> categoryProfiles,
        List<String> insights,
        DataSufficiency dataSufficiency
) {}
