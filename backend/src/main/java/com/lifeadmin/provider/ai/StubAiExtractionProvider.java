package com.lifeadmin.provider.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.lifeadmin.extraction.DateType;
import com.lifeadmin.extraction.FieldSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, keyword-based {@link AiExtractionProvider} used when no cloud model is configured
 * (keyless local runs and tests). It classifies from the OCR text/filename and now reads <b>real
 * important dates</b> from the OCR text via {@link DateTextParser} (falling back to synthetic
 * placeholder dates only when the text contains none). Fields stay representative — full field
 * extraction remains out of scope for the stub. It still demonstrates <b>derived</b> dates (e.g. a
 * warranty expiration = purchase + 2 years, marked {@link FieldSource#DERIVED} per spec §9/§25),
 * recomputed from the real purchase date when one is read. It is <em>not</em> a real model — swap in
 * a real provider by setting {@code lifeadmin.ai.provider} and providing that bean.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.ai", name = "provider", havingValue = "stub", matchIfMissing = true)
public class StubAiExtractionProvider implements AiExtractionProvider {

    private static final BigDecimal HIGH = new BigDecimal("0.95");
    private static final BigDecimal MED = new BigDecimal("0.80");
    // Low confidence flags an unlabeled best-guess date so the UI can prompt the user to confirm it.
    private static final BigDecimal LOW = new BigDecimal("0.45");

    /**
     * Whether ambiguous numeric dates (e.g. {@code 03/04/2026}) are day-first. PH documents commonly
     * use MM/DD, so this defaults false; override with {@code lifeadmin.ai.date-day-first=true}.
     */
    private final boolean dayFirst;

    /** Admin-configurable type keywords / relevant date types / labels (V7). */
    private final com.lifeadmin.admin.doctype.DocumentTypeConfigService typeConfig;

    public StubAiExtractionProvider(
            @org.springframework.beans.factory.annotation.Value("${lifeadmin.ai.date-day-first:false}")
            final boolean dayFirst,
            final com.lifeadmin.admin.doctype.DocumentTypeConfigService typeConfig) {
        this.dayFirst = dayFirst;
        this.typeConfig = typeConfig;
    }

    @Override
    public ExtractionResult classifyAndExtract(final String text, final String fileName) {
        final var haystack = ((text == null ? "" : text) + " " + (fileName == null ? "" : fileName))
                .toLowerCase(Locale.ROOT);
        // Type code (a document_type_config.type_code; built-in or admin-created).
        final var type = typeConfig.classify(haystack);
        final var fields = new ArrayList<ExtractedFieldResult>();
        final var dates = new ArrayList<ImportantDateResult>();
        final var actions = new ArrayList<String>();
        final var today = LocalDate.now();

        // Read real dates from the OCR text. When found, these replace the synthetic placeholders
        // below so the important dates reflect the actual document (spec §9). Fields/actions stay
        // representative (full field extraction remains out of scope for the stub).
        final var parsedDates = readRealDates(text, type);

        // Representative synthetic extraction for the built-in types. Admin-created custom types fall
        // through (no synthetic fields), but still get real OCR dates + configured primary-date logic.
        switch (type) {
            case "PASSPORT" -> {
                fields.add(field("holderName", "Juan Dela Cruz", HIGH));
                fields.add(field("documentNumber", "P1234567A", MED));
                dates.add(date(DateType.EXPIRATION, today.plusYears(3), HIGH, FieldSource.OCR));
                actions.add("Renew passport before expiration");
            }
            case "INSURANCE" -> {
                fields.add(field("organization", "ABC Insurance", HIGH));
                fields.add(field("documentNumber", "INS-123456", MED));
                dates.add(date(DateType.EXPIRATION, today.plusMonths(3), HIGH, FieldSource.OCR));
                dates.add(date(DateType.RENEWAL, today.plusMonths(2), MED, FieldSource.AI));
                actions.add("Prepare renewal documents");
                actions.add("Compare renewal quotes");
            }
            case "VEHICLE_REGISTRATION" -> {
                fields.add(field("plateNumber", "ABC-1234", HIGH));
                dates.add(date(DateType.EXPIRATION, today.plusDays(43), HIGH, FieldSource.OCR));
                actions.add("Renew registration");
                actions.add("Check insurance is current");
            }
            case "WARRANTY", "RECEIPT" -> {
                fields.add(field("organization", "Samsung", HIGH));
                fields.add(field("warrantyTermYears", "2", MED));
                final var purchase = today.minusDays(10);
                dates.add(date(DateType.PURCHASE, purchase, HIGH, FieldSource.OCR));
                // Derived: warranty expiration = purchase + 2 years (clearly marked DERIVED).
                dates.add(date(DateType.WARRANTY_EXPIRATION, purchase.plusYears(2), MED, FieldSource.DERIVED));
                actions.add("Keep receipt and warranty until expiration");
            }
            case "DRIVERS_LICENSE" -> {
                fields.add(field("holderName", "Juan Dela Cruz", HIGH));
                dates.add(date(DateType.EXPIRATION, today.plusYears(2), HIGH, FieldSource.OCR));
                actions.add("Renew driver's license before expiration");
            }
            case "CONTRACT" -> {
                dates.add(date(DateType.CONTRACT_START, today.minusMonths(1), MED, FieldSource.OCR));
                dates.add(date(DateType.CONTRACT_END, today.plusMonths(11), MED, FieldSource.OCR));
                actions.add("Review renewal terms before contract end");
            }
            case "BILL", "SUBSCRIPTION" -> {
                fields.add(field("organization", "Utility Co", MED));
                dates.add(date(DateType.PAYMENT_DEADLINE, today.plusDays(12), HIGH, FieldSource.OCR));
                actions.add("Pay before the deadline");
            }
            default -> {
                // OTHER / custom / unclassifiable: nothing confidently extracted synthetically.
            }
        }

        // Prefer real dates read off the document over the synthetic placeholders.
        if (!parsedDates.isEmpty()) {
            dates.clear();
            dates.addAll(parsedDates);
            // For warranties, if no explicit expiration was read but a purchase date was, derive the
            // expiration from the *real* purchase date (purchase + 2 years) and mark it DERIVED.
            if ("WARRANTY".equals(type)
                    && parsedDates.stream().noneMatch(d -> d.dateType() == DateType.WARRANTY_EXPIRATION)) {
                parsedDates.stream()
                        .filter(d -> d.dateType() == DateType.PURCHASE)
                        .findFirst()
                        .ifPresent(purchase -> dates.add(date(
                                DateType.WARRANTY_EXPIRATION, purchase.dateValue().plusYears(2),
                                MED, FieldSource.DERIVED)));
            }
        }

        final var confidence = "OTHER".equals(type) ? new BigDecimal("0.40") : HIGH;
        return new ExtractionResult(type, confidence, fields, dates, actions);
    }

    /**
     * Read real dates from the OCR text, conservatively (decision 1b). <b>Keyworded</b> dates (their
     * meaning was identified by nearby wording, e.g. "valid until") are kept as-is at medium
     * confidence. <b>Unlabeled</b> dates are normally dropped — they're usually noise (footer
     * timestamps, reference numbers, etc.) — with one exception: if the document has no keyworded
     * date of its <em>primary</em> type (e.g. an insurance doc with no explicit expiration), the
     * single most-plausible unlabeled date is kept as that primary type at <b>low</b> confidence so
     * it surfaces for the user to confirm rather than being silently lost. All read dates are marked
     * {@link FieldSource#OCR}.
     */
    private List<ImportantDateResult> readRealDates(final String text, final String type) {
        final var found = DateTextParser.parse(text, dayFirst);
        if (found.isEmpty()) {
            return List.of();
        }

        final var results = new ArrayList<ImportantDateResult>();
        // 1) Keep every keyworded date (trustworthy: its type came from surrounding words).
        for (final var f : found) {
            if (f.keyworded()) {
                results.add(new ImportantDateResult(f.type(), f.value(), MED, FieldSource.OCR));
            }
        }

        // 2) If the document's primary date wasn't found via a keyword, fall back to the single most
        //    plausible unlabeled date (low confidence, needs user confirmation). Otherwise drop the
        //    unlabeled dates entirely.
        final var primary = primaryDateType(type);
        final boolean hasPrimary = primary != DateType.OTHER
                && results.stream().anyMatch(d -> d.dateType() == primary);
        if (!hasPrimary && primary != DateType.OTHER) {
            bestUnlabeled(found).ifPresent(f ->
                    results.add(new ImportantDateResult(primary, f.value(), LOW, FieldSource.OCR)));
        }
        return results;
    }

    /**
     * Pick the most plausible unlabeled date to stand in for a missing primary (usually an
     * expiration/deadline): prefer the latest future date; if none is in the future, the latest one.
     */
    private static java.util.Optional<DateTextParser.FoundDate> bestUnlabeled(
            final List<DateTextParser.FoundDate> found) {
        final var today = LocalDate.now();
        final var unlabeled = found.stream().filter(f -> !f.keyworded()).toList();
        return unlabeled.stream()
                .filter(f -> !f.value().isBefore(today))
                .max(java.util.Comparator.comparing(DateTextParser.FoundDate::value))
                .or(() -> unlabeled.stream()
                        .max(java.util.Comparator.comparing(DateTextParser.FoundDate::value)));
    }

    /**
     * The date a document of this type most likely carries, used when the text gives no keyword.
     * Prefers the admin-configured relevant date types (first one); falls back to a built-in default.
     */
    private DateType primaryDateType(final String type) {
        final var configured = typeConfig.relevantDateTypes(type);
        if (!configured.isEmpty()) {
            return configured.iterator().next();
        }
        return switch (type) {
            case "PASSPORT", "DRIVERS_LICENSE", "INSURANCE", "VEHICLE_REGISTRATION", "LICENSE",
                 "CERTIFICATE", "GOVERNMENT_DOCUMENT", "PROPERTY_DOCUMENT" -> DateType.EXPIRATION;
            case "WARRANTY" -> DateType.WARRANTY_EXPIRATION;
            case "CONTRACT" -> DateType.CONTRACT_END;
            case "BILL", "SUBSCRIPTION" -> DateType.PAYMENT_DEADLINE;
            default -> DateType.OTHER;
        };
    }

    private static ExtractedFieldResult field(final String name, final String value, final BigDecimal conf) {
        return new ExtractedFieldResult(name, value, value, conf, FieldSource.AI);
    }

    private static ImportantDateResult date(final DateType type, final LocalDate value,
                                            final BigDecimal conf, final FieldSource source) {
        return new ImportantDateResult(type, value, conf, source);
    }
}
