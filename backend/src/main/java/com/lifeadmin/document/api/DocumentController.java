package com.lifeadmin.document.api;

import java.io.IOException;
import java.util.UUID;

import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.document.DocumentService;
import com.lifeadmin.document.DocumentStatus;
import com.lifeadmin.document.api.DocumentDtos.DocumentDetail;
import com.lifeadmin.document.api.DocumentDtos.DocumentSummary;
import com.lifeadmin.document.api.DocumentDtos.DateRequest;
import com.lifeadmin.document.api.DocumentDtos.DateView;
import com.lifeadmin.document.api.DocumentDtos.DownloadUrlResponse;
import com.lifeadmin.document.api.DocumentDtos.UpdateDocumentRequest;
import com.lifeadmin.document.api.DocumentDtos.UploadResponse;
import com.lifeadmin.document.api.DocumentDtos.VerifyRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Document API (API_SPEC §2). All endpoints require authentication and are scoped to the caller's
 * account. Upload is async-friendly (returns 202); AI processing arrives in Phase 3.
 */
@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") final MultipartFile file,
            @RequestParam(value = "personId", required = false) final String personId) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("VALIDATION_ERROR", "A file is required");
        }
        final byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (final IOException e) {
            throw new ValidationException("VALIDATION_ERROR", "Could not read the uploaded file");
        }
        final var response = documentService.upload(bytes, file.getOriginalFilename(), personId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public Page<DocumentSummary> list(
            @RequestParam(value = "status", required = false) final DocumentStatus status,
            @PageableDefault(size = 20) final Pageable pageable) {
        return documentService.list(status, pageable);
    }

    @GetMapping("/{id}")
    public DocumentDetail get(@PathVariable final UUID id) {
        return documentService.get(id);
    }

    @PatchMapping("/{id}")
    public DocumentDetail update(
            @PathVariable final UUID id, @Valid @RequestBody final UpdateDocumentRequest request) {
        return documentService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable final UUID id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/download")
    public DownloadUrlResponse download(@PathVariable final UUID id) {
        return documentService.downloadUrl(id);
    }

    /** Re-enqueue processing (e.g. retry after FAILED). */
    @PostMapping("/{id}/process")
    public ResponseEntity<Void> process(@PathVariable final UUID id) {
        documentService.reprocess(id);
        return ResponseEntity.accepted().build();
    }

    /** Submit verified/corrected extracted data → document becomes ACTIVE. */
    @PostMapping("/{id}/verify")
    public DocumentDetail verify(@PathVariable final UUID id, @Valid @RequestBody final VerifyRequest request) {
        return documentService.verify(id, request);
    }

    // --- Important dates (manual) ---

    @PostMapping("/{id}/dates")
    public ResponseEntity<DateView> addDate(@PathVariable final UUID id, @RequestBody final DateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.addDate(id, request));
    }

    @PatchMapping("/{id}/dates/{dateId}")
    public DateView updateDate(
            @PathVariable final UUID id, @PathVariable final UUID dateId, @RequestBody final DateRequest request) {
        return documentService.updateDate(id, dateId, request);
    }

    @DeleteMapping("/{id}/dates/{dateId}")
    public ResponseEntity<Void> deleteDate(@PathVariable final UUID id, @PathVariable final UUID dateId) {
        documentService.deleteDate(id, dateId);
        return ResponseEntity.noContent().build();
    }
}
