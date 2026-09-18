package com.lifeadmin.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Ownership-scoped fetch: a document is only visible to its owning account. */
    Optional<Document> findByIdAndAccountId(UUID id, UUID accountId);

    Page<Document> findByAccountId(UUID accountId, Pageable pageable);

    /** All documents for an account (used by full account export / delete). */
    List<Document> findByAccountId(UUID accountId);

    /**
     * Full-text + trigram search within an account (Phase 5, G9). Matches the document's own
     * metadata (title/file name/type) or any of its extracted field values, against a Postgres
     * {@code plainto_tsquery} plus an ILIKE prefix fallback so short/partial terms still hit.
     * Ordered newest-first, paged. Native query — {@code simple} config avoids language stemming
     * surprises on mixed-locale (e.g. PH) content.
     */
    @Query(value = """
            select distinct d.* from document d
            left join extracted_field f on f.document_id = d.id
            where d.account_id = :accountId
              and (
                to_tsvector('simple',
                    coalesce(d.title,'') || ' ' || coalesce(d.file_name,'') || ' ' || coalesce(d.document_type,''))
                    @@ plainto_tsquery('simple', :q)
                or to_tsvector('simple', coalesce(f.field_value,'')) @@ plainto_tsquery('simple', :q)
                or d.title ilike '%' || :q || '%'
                or f.field_value ilike '%' || :q || '%'
              )
            order by d.created_at desc
            """,
            countQuery = """
            select count(distinct d.id) from document d
            left join extracted_field f on f.document_id = d.id
            where d.account_id = :accountId
              and (
                to_tsvector('simple',
                    coalesce(d.title,'') || ' ' || coalesce(d.file_name,'') || ' ' || coalesce(d.document_type,''))
                    @@ plainto_tsquery('simple', :q)
                or to_tsvector('simple', coalesce(f.field_value,'')) @@ plainto_tsquery('simple', :q)
                or d.title ilike '%' || :q || '%'
                or f.field_value ilike '%' || :q || '%'
              )
            """,
            nativeQuery = true)
    Page<Document> search(@Param("accountId") UUID accountId, @Param("q") String q, Pageable pageable);

    Page<Document> findByAccountIdAndStatus(UUID accountId, DocumentStatus status, Pageable pageable);

    /** Count of non-archived documents, used for quota enforcement (FREE plan limit). */
    long countByAccountIdAndStatusNot(UUID accountId, DocumentStatus status);

    long countByAccountIdAndStatus(UUID accountId, DocumentStatus status);

    // --- Admin (cross-account) ---

    long countByAccountId(UUID accountId);

    long countByStatus(DocumentStatus status);

    // --- Analytics (cross-account funnel) ---

    /** Number of distinct accounts that have uploaded at least one document. */
    @Query("select count(distinct d.accountId) from Document d")
    long countDistinctAccountsWithDocument();

    /** Number of distinct accounts that have at least one document in the given status. */
    @Query("select count(distinct d.accountId) from Document d where d.status = :status")
    long countDistinctAccountsWithStatus(@Param("status") DocumentStatus status);

    /**
     * Distinct accounts with a document that has moved past processing (i.e. reached
     * REVIEW_REQUIRED, ACTIVE, or ARCHIVED). Used for the "processed" funnel stage.
     */
    @Query("""
            select count(distinct d.accountId) from Document d
            where d.status in (com.lifeadmin.document.DocumentStatus.REVIEW_REQUIRED,
                               com.lifeadmin.document.DocumentStatus.ACTIVE,
                               com.lifeadmin.document.DocumentStatus.ARCHIVED)
            """)
    long countDistinctAccountsWithProcessedDocument();

    /** All documents, newest first, for the admin browser. */
    Page<Document> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Document> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /** Reassign every document of {@code fromType} to {@code toType} (used when a type is deleted). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Document d set d.documentType = :toType where d.documentType = :fromType")
    int reassignType(@Param("fromType") String fromType, @Param("toType") String toType);
}
