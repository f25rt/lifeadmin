package com.lifeadmin.account.data;

import com.lifeadmin.account.data.AccountDataDtos.AccountExport;
import com.lifeadmin.account.data.AccountDataDtos.DeleteAccountRequest;
import com.lifeadmin.security.auth.CurrentUser;
import com.lifeadmin.subscription.QuotaService;
import com.lifeadmin.subscription.QuotaService.UsageView;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service account data endpoints (Phase 5, G5). Both act on the authenticated caller's own
 * account only. {@code /account} sits under the {@code /api/v1} prefix applied by WebConfig, so it
 * requires a valid access token like every other non-public route.
 */
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountDataController {

    private final AccountDataService accountDataService;
    private final QuotaService quotaService;
    private final CurrentUser currentUser;

    /** Current document usage vs. the plan limit, for the dashboard/upload UI. */
    @GetMapping("/usage")
    public UsageView usage() {
        return quotaService.usage(currentUser.require().accountId());
    }

    /** Download a full JSON export of the caller's account data. */
    @GetMapping("/export")
    public ResponseEntity<AccountExport> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lifeadmin-export.json\"")
                .body(accountDataService.export());
    }

    /** Permanently delete the caller's account and all its data (requires email confirmation). */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@RequestBody final DeleteAccountRequest request) {
        accountDataService.deleteAccount(request.confirmEmail());
        return ResponseEntity.noContent().build();
    }
}
