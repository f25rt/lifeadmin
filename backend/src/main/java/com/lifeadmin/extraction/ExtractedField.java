package com.lifeadmin.extraction;

import java.math.BigDecimal;
import java.util.UUID;

import com.lifeadmin.common.persistence.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A structured field extracted from a document (maps to {@code extracted_field}, V3). Carries its
 * {@link FieldSource} and confidence so uncertainty is visible; {@code verified} flips true when the
 * user confirms/edits it (source then becomes USER). {@code rawValue} keeps the pre-normalized text.
 */
@Entity
@Table(name = "extracted_field")
@Getter
@Setter
@NoArgsConstructor
public class ExtractedField extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "field_name", nullable = false, length = 64)
    private String fieldName;

    @Column(name = "field_value", length = 2000)
    private String fieldValue;

    @Column(name = "raw_value", length = 2000)
    private String rawValue;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FieldSource source;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;
}
