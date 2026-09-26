package com.spendsense.assistant.tool.date;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic date and period resolver for natural language financial queries.
 * Strictly uses ZoneId.of("Asia/Kolkata") to avoid host timezone drift.
 */
public final class NaturalLanguageDateResolver {

    public static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^(\\d{4})-(0[1-9]|1[0-2])$");
    private static final Pattern MONTH_YEAR_TEXT_PATTERN = Pattern.compile(
            "^(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{4})$",
            Pattern.CASE_INSENSITIVE
    );

    private NaturalLanguageDateResolver() {}

    public static LocalDate today() {
        return LocalDate.now(IST_ZONE);
    }

    public static DateRange resolveRange(String expression) {
        return resolveRange(expression, today());
    }

    public static DateRange resolveRange(String expression, LocalDate referenceDate) {
        LocalDate now = referenceDate != null ? referenceDate : today();
        if (expression == null || expression.isBlank()) {
            // Default to current month to date
            return new DateRange(now.withDayOfMonth(1), now);
        }

        String normalized = expression.trim().toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "today":
                return new DateRange(now, now);
            case "yesterday":
                LocalDate y = now.minusDays(1);
                return new DateRange(y, y);
            case "this month":
            case "current month":
                return new DateRange(now.withDayOfMonth(1), now);
            case "last month":
            case "previous month":
                YearMonth prevYm = YearMonth.from(now.minusMonths(1));
                return new DateRange(prevYm.atDay(1), prevYm.atEndOfMonth());
            case "this week":
            case "current week":
                LocalDate startOfWeek = now.with(DayOfWeek.MONDAY);
                return new DateRange(startOfWeek, now);
            case "last week":
            case "previous week":
                LocalDate prevWeekMon = now.minusWeeks(1).with(DayOfWeek.MONDAY);
                LocalDate prevWeekSun = prevWeekMon.plusDays(6);
                return new DateRange(prevWeekMon, prevWeekSun);
            case "this year":
            case "current year":
                return new DateRange(now.withDayOfYear(1), now);
            case "last year":
            case "previous year":
                int lastYear = now.getYear() - 1;
                return new DateRange(LocalDate.of(lastYear, 1, 1), LocalDate.of(lastYear, 12, 31));
            case "last 30 days":
            case "past 30 days":
                return new DateRange(now.minusDays(30), now);
            case "last 7 days":
            case "past 7 days":
                return new DateRange(now.minusDays(7), now);
            default:
                break;
        }

        Matcher ymMatcher = YEAR_MONTH_PATTERN.matcher(normalized);
        if (ymMatcher.matches()) {
            YearMonth ym = YearMonth.parse(normalized);
            return new DateRange(ym.atDay(1), ym.atEndOfMonth());
        }

        Matcher monthYearMatcher = MONTH_YEAR_TEXT_PATTERN.matcher(normalized);
        if (monthYearMatcher.matches()) {
            String monthStr = monthYearMatcher.group(1).toUpperCase(Locale.ROOT);
            int year = Integer.parseInt(monthYearMatcher.group(2));
            Month month = Month.valueOf(monthStr);
            YearMonth ym = YearMonth.of(year, month);
            return new DateRange(ym.atDay(1), ym.atEndOfMonth());
        }

        try {
            LocalDate parsed = LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE);
            return new DateRange(parsed, parsed);
        } catch (Exception ignored) {}

        // Fallback: current month to date
        return new DateRange(now.withDayOfMonth(1), now);
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {
        public DateRange {
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                // Swap if inverted
                LocalDate tmp = startDate;
                startDate = endDate;
                endDate = tmp;
            }
        }
    }
}
