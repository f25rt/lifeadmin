package com.lifeadmin.admin.doctype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.lifeadmin.common.error.ConflictException;
import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.document.DocumentType;
import com.lifeadmin.extraction.DateType;
import com.lifeadmin.security.auth.CurrentUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and manages the admin-configurable document-type table (V7). This is the single source of
 * truth for: which types are enabled, their display labels, the keywords that classify text to a
 * type, the date types relevant to each, and default reminder offsets. The AI classifier and date
 * extractor consult this instead of hardcoded lists, so an admin can tune behavior without a deploy.
 */
@Service
@RequiredArgsConstructor
public class DocumentTypeConfigService {

    /** The catch-all type; cannot be created or deleted, and is the reassignment target on delete. */
    static final String OTHER = "OTHER";

    /** Built-in codes (the original enum) — protected from deletion. */
    private static final Set<String> BUILT_IN = Arrays.stream(DocumentType.values())
            .map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final DocumentTypeConfigRepository repository;
    private final DocumentRepository documentRepository;
    private final CurrentUser currentUser;

    // --- Read API used by the AI pipeline ---

    /**
     * Classify text to the code of the first enabled type whose keywords appear, honoring sort
     * order. Falls back to {@code OTHER}. Case-insensitive substring match. Returns the type
     * <em>code</em> (a config {@code type_code}, which may be a built-in or an admin-created type).
     */
    @Transactional(readOnly = true)
    public String classify(final String haystackLower) {
        for (final var cfg : repository.findByEnabledTrueOrderBySortOrderAsc()) {
            if (OTHER.equals(cfg.getTypeCode())) {
                continue;
            }
            for (final var keyword : csv(cfg.getKeywords())) {
                if (!keyword.isBlank() && haystackLower.contains(keyword)) {
                    return cfg.getTypeCode();
                }
            }
        }
        return OTHER;
    }

    /** Date types the admin marked relevant for a document type code (used to pick a primary date). */
    @Transactional(readOnly = true)
    public Set<DateType> relevantDateTypes(final String typeCode) {
        if (typeCode == null) {
            return Set.of();
        }
        return repository.findById(typeCode)
                .map(c -> parseDateTypes(c.getRelevantDateTypes()))
                .orElse(Set.of());
    }

    /** Default reminder "days before" offsets for a type code (used when suggesting reminders). */
    @Transactional(readOnly = true)
    public List<Integer> defaultOffsets(final String typeCode) {
        if (typeCode == null) {
            return List.of(30);
        }
        return repository.findById(typeCode)
                .map(c -> parseOffsets(c.getDefaultOffsetsDays()))
                .orElse(List.of(30));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(final String typeCode) {
        if (typeCode == null) {
            return true;
        }
        return repository.findById(typeCode).map(DocumentTypeConfig::isEnabled).orElse(true);
    }

    // --- Admin CRUD ---

    @Transactional(readOnly = true)
    public List<DocumentTypeConfig> listAll() {
        return repository.findAllByOrderBySortOrderAsc();
    }

    /** Enabled types (code + label), for the user-facing type picker. */
    @Transactional(readOnly = true)
    public List<DocumentTypeConfig> listEnabled() {
        return repository.findByEnabledTrueOrderBySortOrderAsc();
    }

    @Transactional
    public DocumentTypeConfig update(final String typeCode, final String label, final Boolean enabled,
                                     final String keywords, final String relevantDateTypes,
                                     final String defaultOffsetsDays) {
        final var cfg = repository.findById(typeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Document type", typeCode));
        if (label != null) {
            if (label.isBlank()) {
                throw new ValidationException("VALIDATION_ERROR", "Label cannot be blank");
            }
            cfg.setLabel(label.trim());
        }
        if (enabled != null) {
            cfg.setEnabled(enabled);
        }
        if (keywords != null) {
            cfg.setKeywords(normalizeCsvLower(keywords));
        }
        if (relevantDateTypes != null) {
            // Validate each token is a real DateType.
            for (final var dt : csv(relevantDateTypes)) {
                try {
                    DateType.valueOf(dt.toUpperCase(Locale.ROOT));
                } catch (final IllegalArgumentException e) {
                    throw new ValidationException("VALIDATION_ERROR", "Unknown date type: " + dt);
                }
            }
            cfg.setRelevantDateTypes(normalizeCsvUpper(relevantDateTypes));
        }
        if (defaultOffsetsDays != null) {
            for (final var o : csv(defaultOffsetsDays)) {
                if (!o.matches("\\d{1,4}")) {
                    throw new ValidationException("VALIDATION_ERROR", "Offsets must be whole days: " + o);
                }
            }
            cfg.setDefaultOffsetsDays(normalizeCsvLower(defaultOffsetsDays));
        }
        cfg.setUpdatedBy(currentUser.principal().map(p -> p.email()).orElse("system"));
        return repository.save(cfg);
    }

    /**
     * Create a new admin-defined document type. The code is normalized to UPPER_SNAKE and must be
     * unique and match {@code [A-Z][A-Z0-9_]{0,31}}. Optional AI-template fields are validated the
     * same way as {@link #update}.
     */
    @Transactional
    public DocumentTypeConfig create(final String typeCode, final String label, final String keywords,
                                     final String relevantDateTypes, final String defaultOffsetsDays,
                                     final Integer sortOrder) {
        if (typeCode == null || typeCode.isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Type code is required");
        }
        final var code = typeCode.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!code.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new ValidationException("VALIDATION_ERROR",
                    "Type code must be letters/numbers/underscores (max 32), starting with a letter");
        }
        if (repository.existsById(code)) {
            throw new ConflictException("A document type with code '" + code + "' already exists");
        }
        if (label == null || label.isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "Label is required");
        }

        final var cfg = new DocumentTypeConfig();
        cfg.setTypeCode(code);
        cfg.setLabel(label.trim());
        cfg.setEnabled(true);
        cfg.setKeywords(keywords == null ? "" : normalizeCsvLower(keywords));
        cfg.setRelevantDateTypes(relevantDateTypes == null ? "" : validateAndNormalizeDateTypes(relevantDateTypes));
        cfg.setDefaultOffsetsDays(defaultOffsetsDays == null || defaultOffsetsDays.isBlank()
                ? "30" : validateAndNormalizeOffsets(defaultOffsetsDays));
        cfg.setSortOrder(sortOrder == null ? 500 : sortOrder);
        cfg.setUpdatedBy(currentUser.principal().map(p -> p.email()).orElse("system"));
        return repository.save(cfg);
    }

    /**
     * Delete an admin-created document type. Built-in types (the original enum) and {@code OTHER}
     * cannot be deleted. Any documents still using the type are reassigned to {@code OTHER} so no
     * document is left pointing at a missing code.
     */
    @Transactional
    public void delete(final String typeCode) {
        final var code = typeCode == null ? "" : typeCode.trim().toUpperCase(Locale.ROOT);
        if (BUILT_IN.contains(code)) {
            throw new ConflictException("Built-in document types cannot be deleted");
        }
        final var cfg = repository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Document type", typeCode));
        // Reassign any documents on this type to OTHER before removing the config.
        documentRepository.reassignType(code, OTHER);
        repository.delete(cfg);
    }

    // --- helpers ---

    private static String validateAndNormalizeDateTypes(final String csv) {
        for (final var dt : csv(csv)) {
            try {
                DateType.valueOf(dt.toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException e) {
                throw new ValidationException("VALIDATION_ERROR", "Unknown date type: " + dt);
            }
        }
        return normalizeCsvUpper(csv);
    }

    private static String validateAndNormalizeOffsets(final String csv) {
        for (final var o : csv(csv)) {
            if (!o.matches("\\d{1,4}")) {
                throw new ValidationException("VALIDATION_ERROR", "Offsets must be whole days: " + o);
            }
        }
        return normalizeCsvLower(csv);
    }

    private static Set<DateType> parseDateTypes(final String csv) {
        final var set = new LinkedHashSet<DateType>();
        for (final var token : csv(csv)) {
            try {
                set.add(DateType.valueOf(token.toUpperCase(Locale.ROOT)));
            } catch (final IllegalArgumentException ignored) {
                // Skip unknown tokens defensively.
            }
        }
        return set;
    }

    private static List<Integer> parseOffsets(final String csv) {
        final var list = new ArrayList<Integer>();
        for (final var token : csv(csv)) {
            try {
                list.add(Integer.parseInt(token));
            } catch (final NumberFormatException ignored) {
                // skip
            }
        }
        return list.isEmpty() ? List.of(30) : list;
    }

    private static List<String> csv(final String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String normalizeCsvLower(final String csv) {
        return String.join(",", csv(csv).stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
    }

    private static String normalizeCsvUpper(final String csv) {
        return String.join(",", csv(csv).stream().map(s -> s.toUpperCase(Locale.ROOT)).toList());
    }
}
