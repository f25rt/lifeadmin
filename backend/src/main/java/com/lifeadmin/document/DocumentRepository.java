package com.lifeadmin.document;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Ownership-scoped fetch: a document is only visible to its owning account. */
    Optional<Document> findByIdAndAccountId(UUID id, UUID accountId);

    Page<Document> findByAccountId(UUID accountId, Pageable pageable);

    Page<Document> findByAccountIdAndStatus(UUID accountId, DocumentStatus status, Pageable pageable);

    /** Count of non-archived documents, used for quota enforcement (FREE plan limit). */
    long countByAccountIdAndStatusNot(UUID accountId, DocumentStatus status);

    long countByAccountIdAndStatus(UUID accountId, DocumentStatus status);

    // --- Admin (cross-account) ---

    long countByAccountId(UUID accountId);

    long countByStatus(DocumentStatus status);

    /** All documents, newest first, for the admin browser. */
    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Document> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
