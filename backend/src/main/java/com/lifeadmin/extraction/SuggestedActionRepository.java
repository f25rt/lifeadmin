package com.lifeadmin.extraction;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SuggestedActionRepository extends JpaRepository<SuggestedAction, UUID> {
    List<SuggestedAction> findByDocumentIdOrderBySortOrder(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
