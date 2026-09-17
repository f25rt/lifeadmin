package com.lifeadmin.admin.doctype;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-managed configuration for one document type (maps to {@code document_type_config}, V7).
 * Carries both type management ({@link #enabled} + {@link #label}) and the AI extraction template
 * ({@link #keywords} that classify to this type, {@link #relevantDateTypes} the extractor cares
 * about, and {@link #defaultOffsetsDays} for reminders). {@code typeCode} matches the
 * {@link com.lifeadmin.document.DocumentType} enum name.
 */
@Entity
@Table(name = "document_type_config")
@Getter
@Setter
@NoArgsConstructor
public class DocumentTypeConfig {

    @Id
    @Column(name = "type_code", length = 32)
    private String typeCode;

    @Column(name = "label", nullable = false, length = 64)
    private String label;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "keywords", nullable = false)
    private String keywords = "";

    @Column(name = "relevant_date_types", nullable = false)
    private String relevantDateTypes = "";

    @Column(name = "default_offsets_days", nullable = false)
    private String defaultOffsetsDays = "30";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private String updatedBy;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
