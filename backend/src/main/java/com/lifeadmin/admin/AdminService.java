package com.lifeadmin.admin;

import java.util.List;
import java.util.UUID;

import com.lifeadmin.account.AccountRepository;
import com.lifeadmin.account.AppUser;
import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.admin.api.AdminDtos.AdminDocumentRow;
import com.lifeadmin.admin.api.AdminDtos.AdminUserRow;
import com.lifeadmin.admin.api.AdminDtos.OverviewStats;
import com.lifeadmin.document.Document;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.document.DocumentStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform-admin read models (cross-account). All methods here run only for a SUPER_ADMIN (enforced
 * by the security config on {@code /api/v1/admin/**}); they intentionally ignore account scoping,
 * unlike the tenant-facing services.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AppUserRepository userRepository;
    private final AccountRepository accountRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public OverviewStats overview() {
        return new OverviewStats(
                userRepository.count(),
                accountRepository.count(),
                documentRepository.count(),
                documentRepository.countByStatus(DocumentStatus.ACTIVE),
                documentRepository.countByStatus(DocumentStatus.PROCESSING),
                documentRepository.countByStatus(DocumentStatus.FAILED));
    }

    @Transactional(readOnly = true)
    public List<AdminUserRow> listUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AdminDocumentRow> listDocuments(final UUID accountId, final Pageable pageable) {
        final Page<Document> page = (accountId == null)
                ? documentRepository.findAllByOrderByCreatedAtDesc(pageable)
                : documentRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
        return page.map(AdminService::toDocumentRow);
    }

    private AdminUserRow toUserRow(final AppUser u) {
        return new AdminUserRow(
                u.getId().toString(), u.getEmail(), u.getName(), u.getRole().name(),
                u.getAccountId().toString(), u.getTimezone(), u.getCountry(),
                documentRepository.countByAccountId(u.getAccountId()), u.getCreatedAt());
    }

    private static AdminDocumentRow toDocumentRow(final Document d) {
        return new AdminDocumentRow(
                d.getId().toString(), d.getTitle(), d.getFileName(),
                d.getDocumentType() == null ? null : d.getDocumentType().name(),
                d.getStatus().name(), d.getFileSize(), d.getAccountId().toString(),
                d.getCreatedByUserId().toString(), d.getCreatedAt());
    }
}
