package com.lifeadmin.document;

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
 * A stored document (maps to {@code document}, V2). Owned by an {@link com.lifeadmin.account.Account}
 * ({@code accountId} is the ownership key, G12); {@code createdByUserId} records the uploader. The
 * binary lives in object storage — this row holds only metadata + status. {@code documentType} and
 * {@code classificationConfidence} stay null until the Phase 3 AI pipeline fills them.
 */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
public class Document extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "file_name", nullable = false, length = 512)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 128)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    /**
     * The document's type code (matches a {@code document_type_config.type_code}, V7/V10). Stored as
     * a free string rather than an enum so admins can add custom types at runtime; the built-in
     * {@link DocumentType} values remain valid codes.
     */
    @Column(name = "document_type", length = 32)
    private String documentType;

    @Column(name = "classification_confidence")
    private BigDecimal classificationConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private DocumentStatus status = DocumentStatus.UPLOADED;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "source_locale_hint", length = 32)
    private String sourceLocaleHint;
}
