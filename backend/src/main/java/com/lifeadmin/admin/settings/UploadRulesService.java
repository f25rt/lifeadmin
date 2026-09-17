package com.lifeadmin.admin.settings;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.document.UploadProperties;
import com.lifeadmin.security.auth.CurrentUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates the admin-editable upload rules (allowed MIME types, max file size, max PDF
 * pages). Values live in {@link AppSetting}; when unset, the code-level defaults from
 * {@link UploadProperties} apply — so an empty table reproduces the original behavior.
 * {@link com.lifeadmin.document.FileValidator} reads these at request time.
 */
@Service
@RequiredArgsConstructor
public class UploadRulesService {

    /** The only MIME types the pipeline can actually process/normalize today. */
    public static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("application/pdf", "image/jpeg", "image/png");

    static final String KEY_ALLOWED_MIME = "upload.allowedMimeTypes";
    static final String KEY_MAX_SIZE = "upload.maxFileSizeBytes";
    static final String KEY_MAX_PDF_PAGES = "upload.maxPdfPages";

    private final AppSettingRepository settingRepository;
    private final UploadProperties defaults;
    private final CurrentUser currentUser;

    public record UploadRules(Set<String> allowedMimeTypes, long maxFileSizeBytes, int maxPdfPages) {
    }

    @Transactional(readOnly = true)
    public UploadRules current() {
        return new UploadRules(allowedMimeTypes(), maxFileSizeBytes(), maxPdfPages());
    }

    @Transactional(readOnly = true)
    public Set<String> allowedMimeTypes() {
        return settingRepository.findByKey(KEY_ALLOWED_MIME)
                .map(s -> parseCsv(s.getValue()))
                .filter(set -> !set.isEmpty())
                .orElse(new LinkedHashSet<>(SUPPORTED_MIME_TYPES));
    }

    @Transactional(readOnly = true)
    public long maxFileSizeBytes() {
        return settingRepository.findByKey(KEY_MAX_SIZE)
                .map(s -> parseLong(s.getValue(), defaults.getMaxFileSizeBytes()))
                .orElse(defaults.getMaxFileSizeBytes());
    }

    @Transactional(readOnly = true)
    public int maxPdfPages() {
        return settingRepository.findByKey(KEY_MAX_PDF_PAGES)
                .map(s -> (int) parseLong(s.getValue(), defaults.getMaxPdfPages()))
                .orElse(defaults.getMaxPdfPages());
    }

    @Transactional
    public UploadRules update(final Set<String> allowedMimeTypes, final long maxFileSizeBytes,
                              final int maxPdfPages) {
        // Guard: only MIME types the pipeline supports may be enabled, and sizes must be sane.
        final var normalized = new LinkedHashSet<String>();
        for (final var mime : allowedMimeTypes) {
            final var m = mime == null ? "" : mime.trim().toLowerCase();
            if (!SUPPORTED_MIME_TYPES.contains(m)) {
                throw new ValidationException("VALIDATION_ERROR",
                        "Unsupported MIME type '" + mime + "'. Supported: " + SUPPORTED_MIME_TYPES);
            }
            normalized.add(m);
        }
        if (normalized.isEmpty()) {
            throw new ValidationException("VALIDATION_ERROR", "At least one file type must be allowed");
        }
        if (maxFileSizeBytes < 1024 || maxFileSizeBytes > 100L * 1024 * 1024) {
            throw new ValidationException("VALIDATION_ERROR", "maxFileSizeBytes must be between 1KB and 100MB");
        }
        if (maxPdfPages < 1 || maxPdfPages > 500) {
            throw new ValidationException("VALIDATION_ERROR", "maxPdfPages must be between 1 and 500");
        }

        save(KEY_ALLOWED_MIME, String.join(",", normalized));
        save(KEY_MAX_SIZE, Long.toString(maxFileSizeBytes));
        save(KEY_MAX_PDF_PAGES, Integer.toString(maxPdfPages));
        return new UploadRules(normalized, maxFileSizeBytes, maxPdfPages);
    }

    private void save(final String key, final String value) {
        final var setting = settingRepository.findByKey(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        setting.setUpdatedBy(currentUser.principal().map(p -> p.email()).orElse("system"));
        settingRepository.save(setting);
    }

    private static Set<String> parseCsv(final String csv) {
        final var set = new LinkedHashSet<String>();
        if (csv != null) {
            Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(s -> set.add(s.toLowerCase()));
        }
        return set;
    }

    private static long parseLong(final String value, final long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }
}
