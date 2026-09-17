package com.lifeadmin.extraction;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {
    List<ExtractedField> findByDocumentId(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
