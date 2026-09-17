package com.lifeadmin.provider.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Logs emails instead of sending them. Active when {@code lifeadmin.email.provider=log} — used in
 * tests and when no SMTP server is available, so the reminder pipeline runs without a mail transport.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.email", name = "provider", havingValue = "log")
public class LogEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(LogEmailProvider.class);

    @Override
    public void send(final String to, final String subject, final String body) {
        log.info("[email:log] to={} subject='{}' body='{}'", to, subject, body);
    }
}
