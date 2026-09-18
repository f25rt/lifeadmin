package com.lifeadmin.extraction.projection;

import java.time.LocalDate;
import java.util.UUID;

import com.lifeadmin.extraction.DateType;
import com.lifeadmin.extraction.FieldSource;

/**
 * Projection of an important date joined with its document, used to build the dashboard's upcoming
 * list without loading full entities.
 */
public interface AccountDateView {
    UUID getId();
    UUID getDocumentId();
    String getDocumentTitle();
    String getDocumentType();
    DateType getDateType();
    LocalDate getDateValue();
    FieldSource getSource();
}
