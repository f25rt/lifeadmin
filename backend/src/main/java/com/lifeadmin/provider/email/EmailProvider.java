package com.lifeadmin.provider.email;

/**
 * Sends transactional email (spec §16/§36). Behind an interface so the transport (SMTP/Mailpit,
 * SES, SendGrid, …) is swappable. MVP channels are email + in-app.
 */
public interface EmailProvider {
    void send(String to, String subject, String body);
}
