package com.lifeadmin.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.lifeadmin.extraction.DateType;

import org.junit.jupiter.api.Test;

/** Format coverage and ambiguity rules for the deterministic OCR date reader. */
class DateTextParserTest {

    @Test
    void parsesIsoDate() {
        final var found = DateTextParser.parse("Expiry: 2026-09-30", false);
        assertThat(found).singleElement().satisfies(d -> {
            assertThat(d.value()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(d.type()).isEqualTo(DateType.EXPIRATION);
        });
    }

    @Test
    void bareMonthYearResolvesToLastDayOfMonth() {
        assertThat(DateTextParser.parse("valid until 09/2026", false))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.value()).isEqualTo(LocalDate.of(2026, 9, 30));
                    assertThat(d.monthYearOnly()).isTrue();
                    assertThat(d.type()).isEqualTo(DateType.EXPIRATION);
                });
        assertThat(DateTextParser.parse("expires Feb 2027", false))
                .singleElement()
                .satisfies(d -> assertThat(d.value()).isEqualTo(LocalDate.of(2027, 2, 28)));
    }

    @Test
    void spelledMonthFormats() {
        assertThat(DateTextParser.parse("30 September 2026", false))
                .singleElement().satisfies(d -> assertThat(d.value()).isEqualTo(LocalDate.of(2026, 9, 30)));
        assertThat(DateTextParser.parse("Sep 30, 2026", false))
                .singleElement().satisfies(d -> assertThat(d.value()).isEqualTo(LocalDate.of(2026, 9, 30)));
    }

    @Test
    void numericDayFirstVsMonthFirst() {
        // Unambiguous: 30 can only be the day regardless of the flag.
        assertThat(DateTextParser.parse("30/09/2026", false))
                .singleElement().satisfies(d -> assertThat(d.value()).isEqualTo(LocalDate.of(2026, 9, 30)));
        // Ambiguous 03/04/2026: month-first (default) → March 4; day-first → April 3.
        assertThat(DateTextParser.parse("03/04/2026", false))
                .singleElement().satisfies(d -> assertThat(d.value()).isEqualTo(LocalDate.of(2026, 3, 4)));
        assertThat(DateTextParser.parse("03/04/2026", true))
                .singleElement().satisfies(d -> assertThat(d.value()).isEqualTo(LocalDate.of(2026, 4, 3)));
    }

    @Test
    void keywordDrivesDateType() {
        assertThat(DateTextParser.parse("Renewal date: 2026-05-01", false))
                .singleElement().satisfies(d -> assertThat(d.type()).isEqualTo(DateType.RENEWAL));
        assertThat(DateTextParser.parse("Amount due 2026-05-01", false))
                .singleElement().satisfies(d -> assertThat(d.type()).isEqualTo(DateType.PAYMENT_DEADLINE));
    }

    @Test
    void findsMultipleDatesInOrder() {
        final var text = "Start 2026-01-01 and expires 2026-12-31";
        final var found = DateTextParser.parse(text, false);
        assertThat(found).hasSize(2);
        assertThat(found.get(0).value()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(found.get(1).value()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(found.get(1).type()).isEqualTo(DateType.EXPIRATION);
    }

    @Test
    void ignoresTextWithNoDates() {
        assertThat(DateTextParser.parse("no dates here", false)).isEmpty();
        assertThat(DateTextParser.parse("", false)).isEmpty();
        assertThat(DateTextParser.parse(null, false)).isEmpty();
    }

    @Test
    void rejectsImpossibleDates() {
        // 2026-13-40 is not a valid date and must be dropped, not clamped.
        assertThat(DateTextParser.parse("2026-13-40", false)).isEmpty();
    }

    @Test
    void dropsNoiseDates() {
        // Issued / printed / date-of-birth / reference dates are not important dates → dropped.
        assertThat(DateTextParser.parse("Date issued: 2026-01-01", false)).isEmpty();
        assertThat(DateTextParser.parse("Printed on 2026-01-01", false)).isEmpty();
        assertThat(DateTextParser.parse("Date of birth: 1990-05-20", false)).isEmpty();
        assertThat(DateTextParser.parse("Reference 2026-01-01", false)).isEmpty();
    }

    @Test
    void unlabeledDateIsKeptButFlaggedNotKeyworded() {
        final var found = DateTextParser.parse("Some heading 2026-09-30 footer", false);
        assertThat(found).singleElement().satisfies(d -> {
            assertThat(d.value()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(d.keyworded()).isFalse();
            assertThat(d.type()).isEqualTo(DateType.OTHER);
        });
    }

    @Test
    void keywordedDateIsFlaggedKeyworded() {
        assertThat(DateTextParser.parse("expires 2026-09-30", false))
                .singleElement().satisfies(d -> assertThat(d.keyworded()).isTrue());
    }
}
