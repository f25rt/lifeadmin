package com.lifeadmin.provider.email;

import jakarta.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends email via SMTP (Mailpit locally, any SMTP in prod). Active when
 * {@code lifeadmin.email.provider=smtp} (default). Sending is best-effort — a transport failure is
 * logged, not propagated, so a reminder's in-app notification still succeeds.
 */
@Component
@ConditionalOnProperty(prefix = "lifeadmin.email", name = "provider", havingValue = "smtp", matchIfMissing = true)
@RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailProvider.class);

    private final JavaMailSender mailSender;

    @Value("${lifeadmin.email.from:no-reply@lifeadmin.local}")
    private String from;

    @Override
    public void send(final String to, final String subject, final String body) {
        try {
            final MimeMessage message = mailSender.createMimeMessage();
            final var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Sent email to {} ('{}')", to, subject);
        } catch (final Exception e) {
            log.error("Failed to send email to {} ('{}'): {}", to, subject, e.getMessage());
        }
    }
}
