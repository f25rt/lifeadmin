package com.lifeadmin.provider.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.lifeadmin.extraction.DateType;
import com.lifeadmin.extraction.FieldSource;

/**
 * Classifies a document and extracts structured information from its text (spec §24). Returns a
 * strict {@link ExtractionResult}; the pipeline validates and persists it, and the user verifies it
 * before it is trusted (spec §25). Behind an interface so the model provider is swappable.
 */
public interface AiExtractionProvider {

    ExtractionResult classifyAndExtract(String text, String fileName);

    /** The full structured result of classification + extraction. */
    record ExtractionResult(
            String documentType,
            BigDecimal classificationConfidence,
            List<ExtractedFieldResult> fields,
            List<ImportantDateResult> dates,
            List<String> suggestedActions) {
    }

    record ExtractedFieldResult(
            String fieldName,
            String value,
            String rawValue,
            BigDecimal confidence,
            FieldSource source) {
    }

    record ImportantDateResult(
            DateType dateType,
            LocalDate dateValue,
            BigDecimal confidence,
            FieldSource source) {
    }
}
