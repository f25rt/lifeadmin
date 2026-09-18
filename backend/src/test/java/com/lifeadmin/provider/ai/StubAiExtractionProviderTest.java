package com.lifeadmin.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.Set;

import com.lifeadmin.admin.doctype.DocumentTypeConfigService;
import com.lifeadmin.document.DocumentType;
import com.lifeadmin.extraction.DateType;
import com.lifeadmin.extraction.FieldSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StubAiExtractionProviderTest {

    private final DocumentTypeConfigService typeConfig = mock(DocumentTypeConfigService.class);
    private final StubAiExtractionProvider provider = new StubAiExtractionProvider(false, typeConfig);

    @BeforeEach
    void setUpClassifier() {
        // Reproduce the seeded keyword classification the DB config would provide.
        lenient().when(typeConfig.classify(any())).thenAnswer(inv -> {
            final String h = inv.getArgument(0);
            if (h.contains("passport")) return DocumentType.PASSPORT.name();
            if (h.contains("driver") || h.contains("license") || h.contains("licence")) return DocumentType.DRIVERS_LICENSE.name();
            if (h.contains("registration") || h.contains("vehicle") || h.contains("plate")) return DocumentType.VEHICLE_REGISTRATION.name();
            if (h.contains("insurance") || h.contains("policy") || h.contains("insur")) return DocumentType.INSURANCE.name();
            if (h.contains("warranty")) return DocumentType.WARRANTY.name();
            if (h.contains("receipt") || h.contains("invoice")) return DocumentType.RECEIPT.name();
            if (h.contains("contract") || h.contains("agreement") || h.contains("lease")) return DocumentType.CONTRACT.name();
            if (h.contains("bill") || h.contains("statement") || h.contains("amount due")) return DocumentType.BILL.name();
            if (h.contains("subscription")) return DocumentType.SUBSCRIPTION.name();
            if (h.contains("certificate")) return DocumentType.CERTIFICATE.name();
            return DocumentType.OTHER.name();
        });
        // Relevant date types per the seed. Register the general default FIRST, then the specific
        // overrides, so Mockito's "last matching stub wins" resolves the specific ones correctly.
        lenient().when(typeConfig.relevantDateTypes(any())).thenReturn(Set.of());
        lenient().when(typeConfig.relevantDateTypes(DocumentType.INSURANCE.name()))
                .thenReturn(new java.util.LinkedHashSet<>(java.util.List.of(DateType.EXPIRATION, DateType.RENEWAL)));
        lenient().when(typeConfig.relevantDateTypes(DocumentType.WARRANTY.name()))
                .thenReturn(new java.util.LinkedHashSet<>(java.util.List.of(DateType.WARRANTY_EXPIRATION, DateType.PURCHASE)));
    }

    @Test
    void classifiesInsuranceFromKeyword() {
        final var result = provider.classifyAndExtract("ABC Insurance policy number INS-123456", "scan.pdf");
        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE.name());
        assertThat(result.classificationConfidence()).isNotNull();
        assertThat(result.fields()).isNotEmpty();
        assertThat(result.dates()).anyMatch(d -> d.dateType() == DateType.EXPIRATION);
        assertThat(result.suggestedActions()).isNotEmpty();
    }

    @Test
    void classifiesFromFileNameWhenTextIsBlank() {
        final var result = provider.classifyAndExtract("", "my_passport_scan.jpg");
        assertThat(result.documentType()).isEqualTo(DocumentType.PASSPORT.name());
    }

    @Test
    void warrantyProducesADerivedExpirationDate() {
        final var result = provider.classifyAndExtract("Samsung refrigerator warranty", "receipt.jpg");
        assertThat(result.documentType()).isEqualTo(DocumentType.WARRANTY.name());
        // Derived warranty expiration must be marked DERIVED (spec §9).
        assertThat(result.dates())
                .anyMatch(d -> d.dateType() == DateType.WARRANTY_EXPIRATION && d.source() == FieldSource.DERIVED);
        // And it must be later than the (explicit) purchase date.
        final var purchase = result.dates().stream()
                .filter(d -> d.dateType() == DateType.PURCHASE).findFirst().orElseThrow();
        final var warranty = result.dates().stream()
                .filter(d -> d.dateType() == DateType.WARRANTY_EXPIRATION).findFirst().orElseThrow();
        assertThat(warranty.dateValue()).isAfter(purchase.dateValue());
    }

    @Test
    void unknownContentIsClassifiedOtherWithLowConfidence() {
        final var result = provider.classifyAndExtract("random gibberish xyz", "file.pdf");
        assertThat(result.documentType()).isEqualTo(DocumentType.OTHER.name());
        assertThat(result.classificationConfidence().doubleValue()).isLessThan(0.5);
    }

    @Test
    void readsRealExpirationDateFromTextInsteadOfSyntheticPlaceholder() {
        final var text = "INSURANCE POLICY\nPolicy Number: INS-123456\nValid until 09/2026";
        final var result = provider.classifyAndExtract(text, "scan001.png");

        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE.name());
        // The real date is read (Sep 2026 → last day of month) and marked OCR (came off the document).
        final var expiration = result.dates().stream()
                .filter(d -> d.dateType() == DateType.EXPIRATION).findFirst().orElseThrow();
        assertThat(expiration.dateValue()).isEqualTo(java.time.LocalDate.of(2026, 9, 30));
        assertThat(expiration.source()).isEqualTo(FieldSource.OCR);
    }

    @Test
    void dropsUnrelatedNoiseDatesButKeepsTheExpiration() {
        // A policy with an explicit expiration plus a noise "issued" date and a footer print date.
        final var text = "INSURANCE POLICY\nDate issued: 2026-01-05\n"
                + "Valid until 2027-03-31\nPrinted on 2026-01-06";
        final var result = provider.classifyAndExtract(text, "scan.png");

        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE.name());
        // Only the real expiration survives; the issued/printed dates are dropped.
        assertThat(result.dates()).hasSize(1);
        final var only = result.dates().get(0);
        assertThat(only.dateType()).isEqualTo(DateType.EXPIRATION);
        assertThat(only.dateValue()).isEqualTo(java.time.LocalDate.of(2027, 3, 31));
    }

    @Test
    void unlabeledOnlyDocumentKeepsOneLowConfidencePrimaryDate() {
        // An insurance doc whose only date has no keyword: keep exactly one, as EXPIRATION, LOW conf.
        final var text = "ACME INSURANCE\nPremium paid\n2027-06-30";
        final var result = provider.classifyAndExtract(text, "policy.png");

        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE.name());
        assertThat(result.dates()).hasSize(1);
        final var only = result.dates().get(0);
        assertThat(only.dateType()).isEqualTo(DateType.EXPIRATION);
        assertThat(only.dateValue()).isEqualTo(java.time.LocalDate.of(2027, 6, 30));
        // Low confidence signals "please verify" to the UI.
        assertThat(only.confidence().doubleValue()).isLessThan(0.5);
        assertThat(only.source()).isEqualTo(FieldSource.OCR);
    }

    @Test
    void warrantyDerivesExpirationFromRealPurchaseDate() {
        final var text = "Samsung warranty\nDate of purchase: 2026-01-15";
        final var result = provider.classifyAndExtract(text, "receipt.jpg");

        final var purchase = result.dates().stream()
                .filter(d -> d.dateType() == DateType.PURCHASE).findFirst().orElseThrow();
        assertThat(purchase.dateValue()).isEqualTo(java.time.LocalDate.of(2026, 1, 15));
        // Derived expiration must be exactly purchase + 2 years, computed from the REAL purchase.
        final var warranty = result.dates().stream()
                .filter(d -> d.dateType() == DateType.WARRANTY_EXPIRATION).findFirst().orElseThrow();
        assertThat(warranty.dateValue()).isEqualTo(java.time.LocalDate.of(2028, 1, 15));
        assertThat(warranty.source()).isEqualTo(FieldSource.DERIVED);
    }
}

