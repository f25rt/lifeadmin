package com.lifeadmin.audit;

import java.util.UUID;

import com.lifeadmin.common.web.CorrelationIdFilter;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit entries (spec §18, G5), stamping the current request's correlation id. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(final UUID accountId, final UUID actorUserId, final String action,
                       final String entityType, final String entityId, final String detail) {
        final var log = new AuditLog();
        log.setAccountId(accountId);
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetail(detail);
        log.setCorrelationId(MDC.get(CorrelationIdFilter.MDC_KEY));
        repository.save(log);
    }
}
