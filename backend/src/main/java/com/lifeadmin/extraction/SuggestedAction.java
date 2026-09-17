package com.lifeadmin.extraction;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An AI-proposed next step for a document (maps to {@code suggested_action}, V3; spec §10).
 * Presented as a suggestion the user can check off — never as a guaranteed requirement.
 */
@Entity
@Table(name = "suggested_action")
@Getter
@Setter
@NoArgsConstructor
public class SuggestedAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "done", nullable = false)
    private boolean done = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
