package com.lifeadmin.admin.doctype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.common.error.ValidationException;
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

    private final DocumentTypeConfigRepository repository;
    private final CurrentUser currentUser;

    // --- Read API used by the AI pipeline ---

    /**
     * Classify text to the first enabled type whose keywords appear, honoring sort order. Falls back
     * to {@link DocumentType#OTHER}. Case-insensitive substring match, mirroring the previous stub.
     */
    @Transactional(readOnly = true)
    public DocumentType classify(final String haystackLower) {
        for (final var cfg : repository.findByEnabledTrueOrderBySortOrderAsc()) {
            final var type = parseType(cfg.getTypeCode());
            if (type.isEmpty() || type.get() == DocumentType.OTHER) {
                continue;
            }
            for (final var keyword : csv(cfg.getKeywords())) {
                if (!keyword.isBlank() && haystackLower.contains(keyword)) {
                    return type.get();
                }
            }
        }
        return DocumentType.OTHER;
    }

    /** Date types the admin marked relevant for a document type (used to pick a primary date). */
    @Transactional(readOnly = true)
    public Set<DateType> relevantDateTypes(final DocumentType type) {
        return repository.findById(type.name())
                .map(c -> parseDateTypes(c.getRelevantDateTypes()))
                .orElse(Set.of());
    }

    /** Default reminder "days before" offsets for a type (used when suggesting reminders). */
    @Transactional(readOnly = true)
    public List<Integer> defaultOffsets(final DocumentType type) {
        return repository.findById(type.name())
                .map(c -> parseOffsets(c.getDefaultOffsetsDays()))
                .orElse(List.of(30));
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(final DocumentType type) {
        return repository.findById(type.name()).map(DocumentTypeConfig::isEnabled).orElse(true);
    }

    // --- Admin CRUD ---

    @Transactional(readOnly = true)
    public List<DocumentTypeConfig> listAll() {
        return repository.findAllByOrderBySortOrderAsc();
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

    // --- helpers ---

    private static Optional<DocumentType> parseType(final String code) {
        try {
            return Optional.of(DocumentType.valueOf(code));
        } catch (final IllegalArgumentException e) {
            return Optional.empty();
        }
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
