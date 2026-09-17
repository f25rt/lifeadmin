package com.lifeadmin.admin.api;

import java.util.UUID;

import java.util.ArrayList;
import java.util.List;

import com.lifeadmin.admin.AdminService;
import com.lifeadmin.admin.api.AdminDtos.AdminDocumentRow;
import com.lifeadmin.admin.api.AdminDtos.AdminUserList;
import com.lifeadmin.admin.api.AdminDtos.OverviewStats;
import com.lifeadmin.admin.api.AdminDtos.DocumentTypeList;
import com.lifeadmin.admin.api.AdminDtos.DocumentTypeView;
import com.lifeadmin.admin.api.AdminDtos.UpdateDocumentTypeRequest;
import com.lifeadmin.admin.api.AdminDtos.UpdateUploadRulesRequest;
import com.lifeadmin.admin.api.AdminDtos.UploadRulesView;
import com.lifeadmin.admin.doctype.DocumentTypeConfig;
import com.lifeadmin.admin.doctype.DocumentTypeConfigService;
import com.lifeadmin.admin.settings.UploadRulesService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform admin API (SUPER_ADMIN only; enforced by SecurityConfig on {@code /api/v1/admin/**}).
 * Read models for the admin dashboards: overview stats, users, and a cross-account document browser.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final UploadRulesService uploadRules;
    private final DocumentTypeConfigService documentTypes;

    @GetMapping("/overview")
    public OverviewStats overview() {
        return adminService.overview();
    }

    // --- Upload rules ---

    @GetMapping("/settings/upload")
    public UploadRulesView uploadRules() {
        final var rules = uploadRules.current();
        return new UploadRulesView(
                new ArrayList<>(rules.allowedMimeTypes()),
                new ArrayList<>(UploadRulesService.SUPPORTED_MIME_TYPES),
                rules.maxFileSizeBytes(),
                rules.maxPdfPages());
    }

    @PutMapping("/settings/upload")
    public UploadRulesView updateUploadRules(@RequestBody final UpdateUploadRulesRequest request) {
        final var current = uploadRules.current();
        final List<String> allowed = request.allowedMimeTypes() != null
                ? request.allowedMimeTypes() : new ArrayList<>(current.allowedMimeTypes());
        final long maxSize = request.maxFileSizeBytes() != null
                ? request.maxFileSizeBytes() : current.maxFileSizeBytes();
        final int maxPages = request.maxPdfPages() != null
                ? request.maxPdfPages() : current.maxPdfPages();
        final var updated = uploadRules.update(new java.util.LinkedHashSet<>(allowed), maxSize, maxPages);
        return new UploadRulesView(
                new ArrayList<>(updated.allowedMimeTypes()),
                new ArrayList<>(UploadRulesService.SUPPORTED_MIME_TYPES),
                updated.maxFileSizeBytes(),
                updated.maxPdfPages());
    }

    // --- Document types + AI templates ---

    @GetMapping("/document-types")
    public DocumentTypeList documentTypes() {
        final var types = documentTypes.listAll().stream().map(AdminController::toTypeView).toList();
        return new DocumentTypeList(types);
    }

    @PatchMapping("/document-types/{typeCode}")
    public DocumentTypeView updateDocumentType(
            @PathVariable final String typeCode, @RequestBody final UpdateDocumentTypeRequest request) {
        final var updated = documentTypes.update(
                typeCode, request.label(), request.enabled(), request.keywords(),
                request.relevantDateTypes(), request.defaultOffsetsDays());
        return toTypeView(updated);
    }

    @GetMapping("/users")
    public AdminUserList users() {
        return new AdminUserList(adminService.listUsers());
    }

    @GetMapping("/documents")
    public Page<AdminDocumentRow> documents(
            @RequestParam(value = "accountId", required = false) final UUID accountId,
            @PageableDefault(size = 25) final Pageable pageable) {
        return adminService.listDocuments(accountId, pageable);
    }

    private static DocumentTypeView toTypeView(final DocumentTypeConfig c) {
        return new DocumentTypeView(
                c.getTypeCode(), c.getLabel(), c.isEnabled(), c.getKeywords(),
                c.getRelevantDateTypes(), c.getDefaultOffsetsDays(), c.getSortOrder());
    }
}
