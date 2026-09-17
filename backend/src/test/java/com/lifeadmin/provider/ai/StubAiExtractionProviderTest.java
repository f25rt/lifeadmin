package com.lifeadmin.provider.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifeadmin.document.DocumentType;
import com.lifeadmin.extraction.DateType;
import com.lifeadmin.extraction.FieldSource;

import org.junit.jupiter.api.Test;

class StubAiExtractionProviderTest {

    private final StubAiExtractionProvider provider = new StubAiExtractionProvider(false);

    @Test
    void classifiesInsuranceFromKeyword() {
        final var result = provider.classifyAndExtract("ABC Insurance policy number INS-123456", "scan.pdf");
        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE);
        assertThat(result.classificationConfidence()).isNotNull();
        assertThat(result.fields()).isNotEmpty();
        assertThat(result.dates()).anyMatch(d -> d.dateType() == DateType.EXPIRATION);
        assertThat(result.suggestedActions()).isNotEmpty();
    }

    @Test
    void classifiesFromFileNameWhenTextIsBlank() {
        final var result = provider.classifyAndExtract("", "my_passport_scan.jpg");
        assertThat(result.documentType()).isEqualTo(DocumentType.PASSPORT);
    }

    @Test
    void warrantyProducesADerivedExpirationDate() {
        final var result = provider.classifyAndExtract("Samsung refrigerator warranty", "receipt.jpg");
        assertThat(result.documentType()).isEqualTo(DocumentType.WARRANTY);
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
        assertThat(result.documentType()).isEqualTo(DocumentType.OTHER);
        assertThat(result.classificationConfidence().doubleValue()).isLessThan(0.5);
    }

    @Test
    void readsRealExpirationDateFromTextInsteadOfSyntheticPlaceholder() {
        final var text = "INSURANCE POLICY\nPolicy Number: INS-123456\nValid until 09/2026";
        final var result = provider.classifyAndExtract(text, "scan001.png");

        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE);
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

        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE);
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

        assertThat(result.documentType()).isEqualTo(DocumentType.INSURANCE);
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
