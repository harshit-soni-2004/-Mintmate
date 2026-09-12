package com.spendsense.transaction;

import java.util.Collections;
import java.util.List;

public enum TransactionCategory {
    FOOD("Food & Dining", List.of("RESTAURANTS", "GROCERIES", "DELIVERY", "CAFES")),
    TRAVEL("Travel & Commute", List.of("FLIGHTS", "HOTELS", "CAB", "PUBLIC_TRANSPORT")),
    SHOPPING("Shopping", List.of("ELECTRONICS", "CLOTHING", "GENERAL")),
    ENTERTAINMENT("Entertainment", List.of("MOVIES", "CONCERTS", "GAMING", "SUBSCRIPTIONS")),
    BILLS("Bills & Utilities", List.of("ELECTRICITY", "INTERNET", "MOBILE", "RENT")),
    HEALTH("Health & Wellness", List.of("MEDICAL", "PHARMACY", "FITNESS")),
    EDUCATION("Education", List.of("COURSES", "BOOKS", "TUITION")),
    PERSONAL("Personal Care", List.of("GROOMING", "LIFESTYLE")),
    OTHER("Other", List.of("GENERAL", "MISCELLANEOUS"));

    private final String displayName;
    private final List<String> subcategories;

    TransactionCategory(String displayName, List<String> subcategories) {
        this.displayName = displayName;
        this.subcategories = Collections.unmodifiableList(subcategories);
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getSubcategories() {
        return subcategories;
    }

    public boolean isValidSubcategory(String subcategory) {
        if (subcategory == null || subcategory.isBlank()) {
            return false;
        }
        return subcategories.stream().anyMatch(sub -> sub.equalsIgnoreCase(subcategory.trim()));
    }
}
