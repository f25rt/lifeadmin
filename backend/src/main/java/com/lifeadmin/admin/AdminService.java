package com.lifeadmin.admin;

import java.util.List;
import java.util.UUID;

import com.lifeadmin.account.AccountRepository;
import com.lifeadmin.account.AppUser;
import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.account.UserRole;
import com.lifeadmin.admin.api.AdminDtos.AdminUserRow;
import com.lifeadmin.admin.api.AdminDtos.AdminDocumentRow;
import com.lifeadmin.admin.api.AdminDtos.OverviewStats;
import com.lifeadmin.audit.AuditService;
import com.lifeadmin.common.error.ConflictException;
import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.common.error.ValidationException;
import com.lifeadmin.document.Document;
import com.lifeadmin.document.DocumentRepository;
import com.lifeadmin.document.DocumentStatus;
import com.lifeadmin.security.auth.CurrentUser;

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
    private final AuditService auditService;
    private final CurrentUser currentUser;

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

    /**
     * Change a user's role. Guardrails: an admin cannot change their own role (prevents self-lockout
     * from SUPER_ADMIN), and cannot demote the last remaining OWNER of an account away from OWNER
     * (every account must keep an owner). The change is audited.
     */
    @Transactional
    public AdminUserRow updateRole(final UUID userId, final String roleName) {
        final var newRole = parseRole(roleName);
        final var actor = currentUser.require();
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getId().equals(actor.userId())) {
            throw new ConflictException("You cannot change your own role");
        }
        final var oldRole = user.getRole();
        if (oldRole == UserRole.OWNER && newRole != UserRole.OWNER && wouldOrphanAccount(user)) {
            throw new ConflictException(
                    "Cannot demote the last OWNER of a multi-user account; promote another user to OWNER first");
        }
        if (oldRole == newRole) {
            return toUserRow(user);
        }

        user.setRole(newRole);
        final var saved = userRepository.save(user);
        auditService.record(user.getAccountId(), actor.userId(), "ADMIN_USER_ROLE_CHANGED",
                "AppUser", user.getId().toString(),
                "role " + oldRole + " -> " + newRole);
        return toUserRow(saved);
    }

    /**
     * Enable or disable (soft-lock login for) a user. A disabled user cannot log in or refresh. An
     * admin cannot disable their own login. The change is audited.
     */
    @Transactional
    public AdminUserRow updateStatus(final UUID userId, final Boolean disabled) {
        if (disabled == null) {
            throw new ValidationException("VALIDATION_ERROR", "'disabled' is required");
        }
        final var actor = currentUser.require();
        final var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getId().equals(actor.userId()) && disabled) {
            throw new ConflictException("You cannot disable your own account");
        }
        if (user.isDisabled() == disabled) {
            return toUserRow(user);
        }

        user.setDisabled(disabled);
        final var saved = userRepository.save(user);
        auditService.record(user.getAccountId(), actor.userId(),
                disabled ? "ADMIN_USER_DISABLED" : "ADMIN_USER_ENABLED",
                "AppUser", user.getId().toString(),
                "disabled -> " + disabled);
        return toUserRow(saved);
    }

    private UserRole parseRole(final String roleName) {
        if (roleName == null || roleName.isBlank()) {
            throw new ValidationException("VALIDATION_ERROR", "'role' is required");
        }
        try {
            return UserRole.valueOf(roleName.trim().toUpperCase());
        } catch (final IllegalArgumentException ex) {
            throw new ValidationException("VALIDATION_ERROR", "Unknown role: " + roleName);
        }
    }

    /**
     * True if demoting {@code user} away from OWNER would leave a <em>multi-user</em> account with no
     * owner. A single-user account (the MVP norm — one OWNER per account) is exempt: that user's role
     * is theirs to change (e.g. promote to SUPER_ADMIN/ADMIN), and there's no one to orphan.
     */
    private boolean wouldOrphanAccount(final AppUser user) {
        final var members = userRepository.findByAccountId(user.getAccountId());
        if (members.size() <= 1) {
            return false;
        }
        return members.stream()
                .filter(u -> u.getRole() == UserRole.OWNER)
                .noneMatch(u -> !u.getId().equals(user.getId()));
    }

    private AdminUserRow toUserRow(final AppUser u) {
        return new AdminUserRow(
                u.getId().toString(), u.getEmail(), u.getName(), u.getRole().name(),
                u.getAccountId().toString(), u.getTimezone(), u.getCountry(), u.isDisabled(),
                documentRepository.countByAccountId(u.getAccountId()), u.getCreatedAt());
    }

    private static AdminDocumentRow toDocumentRow(final Document d) {
        return new AdminDocumentRow(
                d.getId().toString(), d.getTitle(), d.getFileName(),
                d.getDocumentType(),
                d.getStatus().name(), d.getFileSize(), d.getAccountId().toString(),
                d.getCreatedByUserId().toString(), d.getCreatedAt());
    }
}
