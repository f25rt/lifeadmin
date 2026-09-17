package com.lifeadmin.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentArtifactRepository extends JpaRepository<DocumentArtifact, UUID> {

    List<DocumentArtifact> findByDocumentId(UUID documentId);

    Optional<DocumentArtifact> findByDocumentIdAndKind(UUID documentId, ArtifactKind kind);

    void deleteByDocumentId(UUID documentId);
}
