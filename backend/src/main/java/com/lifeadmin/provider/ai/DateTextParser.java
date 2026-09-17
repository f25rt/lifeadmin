package com.lifeadmin.provider.ai;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lifeadmin.extraction.DateType;

/**
 * Deterministic date reader for OCR text (no AI/model). It finds dates in the common formats seen on
 * documents and, from nearby wording, guesses what each date <em>is</em> (expiration, renewal,
 * payment deadline, …). This is intentionally simple and rule-based so it is predictable and testable.
 *
 * <p>Formats recognized:
 * <ul>
 *   <li>ISO: {@code 2026-09-30}</li>
 *   <li>Numeric with / . or -: {@code 30/09/2026}, {@code 09/30/2026}, {@code 30.09.2026}</li>
 *   <li>Spelled month: {@code 30 September 2026}, {@code Sep 30, 2026}, {@code September 2026}</li>
 *   <li>Month/year only: {@code 09/2026}, {@code Sep 2026}</li>
 * </ul>
 *
 * <p>Ambiguity rules: a bare month/year (no day) resolves to the <b>last day of the month</b> — the
 * safe choice for an expiration. For numeric {@code a/b/yyyy}, if one component is &gt; 12 it is the
 * day; otherwise {@code dayFirst} decides (PH forms are commonly {@code MM/DD}, so default false).
 */
public final class DateTextParser {

    private DateTextParser() {
    }

    /**
     * A date found in the text. {@code keyworded} is true when nearby wording identified what the
     * date is (so {@code type} is trustworthy); when false the date is unlabeled and {@code type} is
     * {@link DateType#OTHER}. Consumers use {@code keyworded} to decide how much to trust the date.
     */
    public record FoundDate(DateType type, LocalDate value, int index, boolean monthYearOnly,
                            boolean keyworded) {
    }

    /** Outcome of reading the words around a date. */
    private record Context(DateType type, boolean keyworded, boolean noise) {
    }

    private static final Map<String, Integer> MONTHS = buildMonths();

    private static final String MONTH_NAMES =
            "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|"
                    + "sep(?:t)?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?";

    // ISO 2026-09-30
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");
    // Numeric 30/09/2026 | 09/30/2026 | 30.09.2026 | 30-09-2026
    private static final Pattern NUMERIC =
            Pattern.compile("\\b(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2,4})\\b");
    // Day + spelled month + year: "30 September 2026" / "Sep 30, 2026" / "30 Sep 2026"
    private static final Pattern DMY_WORD = Pattern.compile(
            "\\b(\\d{1,2})\\s+(" + MONTH_NAMES + ")\\.?,?\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MDY_WORD = Pattern.compile(
            "\\b(" + MONTH_NAMES + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})\\b",
            Pattern.CASE_INSENSITIVE);
    // Month + year only: "September 2026" / "Sep 2026"
    private static final Pattern MONTH_YEAR = Pattern.compile(
            "\\b(" + MONTH_NAMES + ")\\.?\\s+(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    // Numeric month/year only: "09/2026"
    private static final Pattern NUM_MONTH_YEAR = Pattern.compile("\\b(\\d{1,2})[/.\\-](\\d{4})\\b");

    /**
     * Parse all dates in {@code text}. Overlapping matches are avoided by claiming character ranges
     * from the most specific pattern to the least. Returns them in the order found.
     */
    public static List<FoundDate> parse(final String text, final boolean dayFirst) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        final var claimed = new boolean[text.length()];
        final var out = new ArrayList<FoundDate>();

        // Full dates first (most specific), so their digits aren't re-consumed by month/year-only.
        collectIso(text, claimed, out);
        collectWord(text, DMY_WORD, true, claimed, out);
        collectWord(text, MDY_WORD, false, claimed, out);
        collectNumeric(text, dayFirst, claimed, out);
        collectMonthYear(text, claimed, out);
        collectNumMonthYear(text, claimed, out);

        out.sort((a, b) -> Integer.compare(a.index(), b.index()));
        return out;
    }

    private static void collectIso(final String text, final boolean[] claimed, final List<FoundDate> out) {
        final Matcher m = ISO.matcher(text);
        while (m.find()) {
            if (isClaimed(claimed, m.start(), m.end())) continue;
            final var date = safeDate(num(m.group(1)), num(m.group(2)), num(m.group(3)));
            if (date != null) {
                claim(claimed, m.start(), m.end());
                final var f = found(text, m.start(), date, false);
                if (f != null) out.add(f);
            }
        }
    }

    private static void collectNumeric(final String text, final boolean dayFirst,
                                       final boolean[] claimed, final List<FoundDate> out) {
        final Matcher m = NUMERIC.matcher(text);
        while (m.find()) {
            if (isClaimed(claimed, m.start(), m.end())) continue;
            final int a = num(m.group(1));
            final int b = num(m.group(2));
            final int year = normalizeYear(num(m.group(3)));
            final int day;
            final int month;
            if (a > 12 && b <= 12) {
                day = a; month = b;
            } else if (b > 12 && a <= 12) {
                month = a; day = b;
            } else {
                day = dayFirst ? a : b;
                month = dayFirst ? b : a;
            }
            final var date = safeDate(year, month, day);
            if (date != null) {
                claim(claimed, m.start(), m.end());
                final var f = found(text, m.start(), date, false);
                if (f != null) out.add(f);
            }
        }
    }

    private static void collectWord(final String text, final Pattern pattern, final boolean dayMonthYear,
                                    final boolean[] claimed, final List<FoundDate> out) {
        final Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (isClaimed(claimed, m.start(), m.end())) continue;
            final int day;
            final int month;
            final int year;
            if (dayMonthYear) {
                day = num(m.group(1));
                month = monthNumber(m.group(2));
                year = num(m.group(3));
            } else {
                month = monthNumber(m.group(1));
                day = num(m.group(2));
                year = num(m.group(3));
            }
            final var date = safeDate(year, month, day);
            if (date != null) {
                claim(claimed, m.start(), m.end());
                final var f = found(text, m.start(), date, false);
                if (f != null) out.add(f);
            }
        }
    }

    private static void collectMonthYear(final String text, final boolean[] claimed, final List<FoundDate> out) {
        final Matcher m = MONTH_YEAR.matcher(text);
        while (m.find()) {
            if (isClaimed(claimed, m.start(), m.end())) continue;
            final int month = monthNumber(m.group(1));
            final int year = num(m.group(2));
            final var date = lastDayOfMonth(year, month);
            if (date != null) {
                claim(claimed, m.start(), m.end());
                final var f = found(text, m.start(), date, true);
                if (f != null) out.add(f);
            }
        }
    }

    private static void collectNumMonthYear(final String text, final boolean[] claimed, final List<FoundDate> out) {
        final Matcher m = NUM_MONTH_YEAR.matcher(text);
        while (m.find()) {
            if (isClaimed(claimed, m.start(), m.end())) continue;
            final int month = num(m.group(1));
            final int year = num(m.group(2));
            final var date = lastDayOfMonth(year, month);
            if (date != null) {
                claim(claimed, m.start(), m.end());
                final var f = found(text, m.start(), date, true);
                if (f != null) out.add(f);
            }
        }
    }

    /**
     * Build a {@link FoundDate}, dropping dates whose surrounding words mark them as noise (issued,
     * printed, date of birth, "as of", reference numbers, …). Returns {@code null} if the date
     * should not be kept, so callers add it only when non-null.
     */
    private static FoundDate found(final String text, final int start, final LocalDate value,
                                   final boolean monthYearOnly) {
        final var ctx = classifyContext(text, start);
        if (ctx.noise()) {
            return null;
        }
        return new FoundDate(ctx.type(), value, start, monthYearOnly, ctx.keyworded());
    }

    /**
     * Read the ~40 chars before the date to (a) reject noise dates and (b) infer the date's type from
     * a keyword. {@code keyworded} is true only when a positive keyword matched; unlabeled dates come
     * back as {@link DateType#OTHER} with {@code keyworded=false} so the caller can treat them
     * cautiously.
     */
    private static Context classifyContext(final String text, final int index) {
        // Only consider text on the SAME line before the date (up to ~40 chars), so a keyword from a
        // previous line (e.g. "Date issued:") can't attach to this line's date.
        int from = Math.max(0, index - 40);
        final int lineStart = text.lastIndexOf('\n', index - 1);
        if (lineStart >= 0 && lineStart + 1 > from) {
            from = lineStart + 1;
        }
        final var context = text.substring(from, index).toLowerCase(Locale.ROOT);

        // Negative signals: these dates are not "important dates" for reminders. Drop them.
        if (contains(context, "issued", "date issued", "printed", "print date", "printed on",
                "generated", "as of", "date of birth", "birth", "dob", "reference", "ref no", "ref:",
                "invoice no", "invoice #", "account no", "receipt no", "or no", "transaction")) {
            return new Context(DateType.OTHER, false, true);
        }

        if (contains(context, "expir", "valid until", "valid thru", "valid through", "expires", "exp ", "exp.")) {
            return new Context(DateType.EXPIRATION, true, false);
        }
        if (contains(context, "renew")) return new Context(DateType.RENEWAL, true, false);
        if (contains(context, "due", "payment", "pay before", "amount due")) {
            return new Context(DateType.PAYMENT_DEADLINE, true, false);
        }
        // "purchase" is more specific than "warranty" (a warranty doc says "date of purchase"), so
        // check it first — otherwise a nearby "warranty" would mislabel the purchase date.
        if (contains(context, "purchase", "bought")) return new Context(DateType.PURCHASE, true, false);
        if (contains(context, "warranty")) return new Context(DateType.WARRANTY_EXPIRATION, true, false);
        if (contains(context, "inspection", "inspect")) return new Context(DateType.INSPECTION, true, false);
        if (contains(context, "start", "effective")) return new Context(DateType.CONTRACT_START, true, false);
        if (contains(context, "end date", "ends", "until", "valid to")) {
            return new Context(DateType.CONTRACT_END, true, false);
        }
        if (contains(context, "appointment", "schedule")) return new Context(DateType.APPOINTMENT, true, false);

        // No keyword: an unlabeled date. Keep it, but mark it un-keyworded (type OTHER).
        return new Context(DateType.OTHER, false, false);
    }

    private static boolean contains(final String haystack, final String... needles) {
        for (final var n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }

    private static int monthNumber(final String monthWord) {
        final var key = monthWord.toLowerCase(Locale.ROOT).substring(0, 3);
        return MONTHS.getOrDefault(key, 0);
    }

    private static int normalizeYear(final int year) {
        if (year >= 100) return year;
        // Two-digit year: assume 2000s (documents in this app are current/future-dated).
        return 2000 + year;
    }

    private static LocalDate safeDate(final int year, final int month, final int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (final DateTimeException e) {
            return null;
        }
    }

    private static LocalDate lastDayOfMonth(final int year, final int month) {
        try {
            return YearMonth.of(year, month).atEndOfMonth();
        } catch (final DateTimeException e) {
            return null;
        }
    }

    private static int num(final String s) {
        return Integer.parseInt(s);
    }

    private static boolean isClaimed(final boolean[] claimed, final int start, final int end) {
        for (int i = start; i < end && i < claimed.length; i++) {
            if (claimed[i]) return true;
        }
        return false;
    }

    private static void claim(final boolean[] claimed, final int start, final int end) {
        for (int i = start; i < end && i < claimed.length; i++) {
            claimed[i] = true;
        }
    }

    private static Map<String, Integer> buildMonths() {
        return Map.ofEntries(
                Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
                Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
                Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));
    }
}
