package com.spendsense.assistant.tool.date;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class NaturalLanguageDateResolverTest {

    @Test
    @DisplayName("Should resolve 'today' and 'yesterday' correctly")
    void shouldResolveTodayAndYesterday() {
        LocalDate ref = LocalDate.of(2026, 9, 24);

        NaturalLanguageDateResolver.DateRange todayRange = NaturalLanguageDateResolver.resolveRange("today", ref);
        assertThat(todayRange.startDate()).isEqualTo(ref);
        assertThat(todayRange.endDate()).isEqualTo(ref);

        NaturalLanguageDateResolver.DateRange yesterdayRange = NaturalLanguageDateResolver.resolveRange("yesterday", ref);
        assertThat(yesterdayRange.startDate()).isEqualTo(LocalDate.of(2026, 9, 23));
        assertThat(yesterdayRange.endDate()).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    @DisplayName("Should resolve 'this month' and 'last month' correctly")
    void shouldResolveThisMonthAndLastMonth() {
        LocalDate ref = LocalDate.of(2026, 9, 24);

        NaturalLanguageDateResolver.DateRange thisMonth = NaturalLanguageDateResolver.resolveRange("this month", ref);
        assertThat(thisMonth.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(thisMonth.endDate()).isEqualTo(LocalDate.of(2026, 9, 24));

        NaturalLanguageDateResolver.DateRange lastMonth = NaturalLanguageDateResolver.resolveRange("last month", ref);
        assertThat(lastMonth.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(lastMonth.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("Should resolve 'this week' and 'last week' correctly")
    void shouldResolveThisWeekAndLastWeek() {
        // 2026-09-24 is a Thursday. Week starts Monday 2026-09-21.
        LocalDate ref = LocalDate.of(2026, 9, 24);

        NaturalLanguageDateResolver.DateRange thisWeek = NaturalLanguageDateResolver.resolveRange("this week", ref);
        assertThat(thisWeek.startDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(thisWeek.endDate()).isEqualTo(ref);

        NaturalLanguageDateResolver.DateRange lastWeek = NaturalLanguageDateResolver.resolveRange("last week", ref);
        assertThat(lastWeek.startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(lastWeek.endDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("Should resolve 'last 30 days' and year expressions")
    void shouldResolvePastDaysAndYear() {
        LocalDate ref = LocalDate.of(2026, 9, 24);

        NaturalLanguageDateResolver.DateRange last30 = NaturalLanguageDateResolver.resolveRange("last 30 days", ref);
        assertThat(last30.startDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(last30.endDate()).isEqualTo(ref);

        NaturalLanguageDateResolver.DateRange lastYear = NaturalLanguageDateResolver.resolveRange("last year", ref);
        assertThat(lastYear.startDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(lastYear.endDate()).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    @DisplayName("Boundary Test 11: 'today' around midnight must resolve using Asia/Kolkata")
    void shouldResolveMidnightBoundaryStrictlyInAsiaKolkata() {
        // At 2026-09-23 18:45:00 UTC, in UTC the date is 2026-09-23.
        // However, in Asia/Kolkata (UTC + 5:30), it is 2026-09-24 00:15:00 (15 minutes past midnight).
        Instant instantAroundMidnightUtc = Instant.parse("2026-09-23T18:45:00Z");
        ZonedDateTime istDateTime = instantAroundMidnightUtc.atZone(NaturalLanguageDateResolver.IST_ZONE);

        assertThat(istDateTime.toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 24));

        NaturalLanguageDateResolver.DateRange range = NaturalLanguageDateResolver.resolveRange("today", istDateTime.toLocalDate());
        assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 9, 24));
        assertThat(range.endDate()).isEqualTo(LocalDate.of(2026, 9, 24));
    }

    @Test
    @DisplayName("Explicit month text 'August 2026' or '2026-08' should resolve full month")
    void shouldResolveExplicitMonths() {
        NaturalLanguageDateResolver.DateRange ymRange = NaturalLanguageDateResolver.resolveRange("2026-08");
        assertThat(ymRange.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(ymRange.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));

        NaturalLanguageDateResolver.DateRange textRange = NaturalLanguageDateResolver.resolveRange("august 2026");
        assertThat(textRange.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(textRange.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    @DisplayName("Sunday boundary: 'this week' on a Sunday spans Monday through Sunday")
    void shouldResolveSundayWeekBoundaryCorrectly() {
        // 2026-09-27 is a Sunday. Monday of that week was 2026-09-21.
        LocalDate sundayRef = LocalDate.of(2026, 9, 27);

        NaturalLanguageDateResolver.DateRange thisWeek = NaturalLanguageDateResolver.resolveRange("this week", sundayRef);
        assertThat(thisWeek.startDate()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(thisWeek.endDate()).isEqualTo(sundayRef);

        NaturalLanguageDateResolver.DateRange lastWeek = NaturalLanguageDateResolver.resolveRange("last week", sundayRef);
        assertThat(lastWeek.startDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(lastWeek.endDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    }

    @Test
    @DisplayName("Year boundary: 'last month' on Jan 1 resolves to December of previous year")
    void shouldResolveYearBoundaryOnJanuaryFirst() {
        LocalDate janFirst = LocalDate.of(2027, 1, 1);

        NaturalLanguageDateResolver.DateRange lastMonth = NaturalLanguageDateResolver.resolveRange("last month", janFirst);
        assertThat(lastMonth.startDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(lastMonth.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));

        NaturalLanguageDateResolver.DateRange lastYear = NaturalLanguageDateResolver.resolveRange("last year", janFirst);
        assertThat(lastYear.startDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(lastYear.endDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    @DisplayName("Should resolve standalone 'August' in October 2026 to August 2026")
    void shouldResolveStandaloneAugustInOctober2026() {
        LocalDate octRef = LocalDate.of(2026, 10, 10);

        NaturalLanguageDateResolver.DateRange augustRange = NaturalLanguageDateResolver.resolveRange("August", octRef);
        assertThat(augustRange.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(augustRange.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));

        NaturalLanguageDateResolver.DateRange inAugust = NaturalLanguageDateResolver.resolveRange("in August", octRef);
        assertThat(inAugust.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(inAugust.endDate()).isEqualTo(LocalDate.of(2026, 8, 31));

        NaturalLanguageDateResolver.DateRange lastMonth = NaturalLanguageDateResolver.resolveRange("last month", octRef);
        assertThat(lastMonth.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(lastMonth.endDate()).isEqualTo(LocalDate.of(2026, 9, 30));

        YearMonth ym = NaturalLanguageDateResolver.resolveYearMonth("August", octRef);
        assertThat(ym).isEqualTo(YearMonth.of(2026, 8));
    }
}
