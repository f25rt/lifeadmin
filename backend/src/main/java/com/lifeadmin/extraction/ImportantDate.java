package com.lifeadmin.extraction;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * An important date on a document (maps to {@code important_date}, V3). A {@link FieldSource#DERIVED}
 * date was calculated (e.g. warranty end = purchase + term) and must be shown as such (spec §9);
 * {@code sourceRuleId} optionally links it to the rule it was derived from.
 */
@Entity
@Table(name = "important_date")
@Getter
@Setter
@NoArgsConstructor
public class ImportantDate extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "date_type", nullable = false, length = 32)
    private DateType dateType;

    @Column(name = "date_value", nullable = false)
    private LocalDate dateValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private FieldSource source;

    @Column(name = "confidence")
    private BigDecimal confidence;

    @Column(name = "source_rule_id")
    private UUID sourceRuleId;
}
