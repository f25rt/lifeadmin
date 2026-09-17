package com.lifeadmin.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        final var props = new JwtProperties();
        props.setSecret("unit-test-secret-that-is-definitely-long-enough-32bytes!");
        props.setAccessTtlMinutes(30);
        props.setRefreshTtlDays(14);
        props.setIssuer("lifeadmin");
        service = new JwtTokenService(props);
    }

    @Test
    void accessTokenRoundTripsClaims() {
        final var userId = UUID.randomUUID();
        final var accountId = UUID.randomUUID();
        final var token = service.createAccessToken(userId, accountId, "juan@example.com", "OWNER");

        final var claims = service.parse(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("accountId", String.class)).isEqualTo(accountId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("juan@example.com");
        assertThat(claims.get("role", String.class)).isEqualTo("OWNER");
    }

    @Test
    void tamperedTokenIsRejected() {
        final var token = service.createAccessToken(UUID.randomUUID(), UUID.randomUUID(), "a@b.com", "OWNER");
        final var tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(Exception.class);
    }

    @Test
    void refreshTokenHashIsDeterministicAndOpaque() {
        final var raw = service.generateRefreshTokenValue();
        assertThat(raw).isNotBlank();
        assertThat(service.hashRefreshToken(raw)).isEqualTo(service.hashRefreshToken(raw));
        assertThat(service.hashRefreshToken(raw)).isNotEqualTo(raw);
    }

    @Test
    void tooShortSecretIsRejected() {
        final var props = new JwtProperties();
        props.setSecret("too-short");
        assertThatThrownBy(() -> new JwtTokenService(props)).isInstanceOf(IllegalStateException.class);
    }
}
