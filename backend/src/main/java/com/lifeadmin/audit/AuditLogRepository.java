package com.lifeadmin.audit;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Retention proxy (no session/login-timestamp table exists): the number of distinct accounts
     * that produced audit activity at least {@code days} after the account was created — i.e. they
     * came back beyond their first {@code days}-day window. Native query so it can join audit_log to
     * account on account_id (there is intentionally no JPA relationship / FK between them).
     */
    @Query(value = """
            select count(distinct a.account_id)
            from audit_log a
            join account acc on acc.id = a.account_id
            where a.occurred_at >= acc.created_at + make_interval(days => :days)
            """, nativeQuery = true)
    long countRetainedAccounts(@Param("days") int days);

    /** Distinct accounts that created earlier than {@code days} ago (eligible to be "retained"). */
    @Query(value = """
            select count(*) from account acc
            where acc.created_at <= now() - make_interval(days => :days)
            """, nativeQuery = true)
    long countAccountsOlderThanDays(@Param("days") int days);
}
