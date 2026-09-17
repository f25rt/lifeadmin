package com.lifeadmin.extraction;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.lifeadmin.extraction.projection.AccountDateView;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImportantDateRepository extends JpaRepository<ImportantDate, UUID> {
    List<ImportantDate> findByDocumentId(UUID documentId);
    Optional<ImportantDate> findByIdAndDocumentId(UUID id, UUID documentId);
    void deleteByDocumentId(UUID documentId);

    /**
     * Upcoming important dates across an account (dashboard). Joins to the owning, non-archived
     * document so results are ownership-scoped (G12); ordered soonest-first.
     */
    @Query("""
            select d.id as id, d.documentId as documentId, doc.title as documentTitle,
                   doc.documentType as documentType, d.dateType as dateType, d.dateValue as dateValue,
                   d.source as source
            from ImportantDate d
            join Document doc on doc.id = d.documentId
            where doc.accountId = :accountId
              and doc.status <> com.lifeadmin.document.DocumentStatus.ARCHIVED
              and d.dateValue >= :from
              and d.dateValue <= :to
            order by d.dateValue asc
            """)
    List<AccountDateView> findUpcomingForAccount(
            @Param("accountId") UUID accountId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
