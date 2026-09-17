package com.lifeadmin.security.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response DTOs for authentication. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100, message = "Password must be 8-100 characters") String password,
            @Size(max = 64) String timezone,
            @Size(max = 8) String country) {
    }

    public record RegisterResponse(String userId, String accountId, String email) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @NotBlank String refreshToken) {
    }

    public record LogoutRequest(
            @NotBlank String refreshToken) {
    }

    /** Successful login/refresh payload. {@code refreshToken} is the raw value, returned once. */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            String userId,
            String accountId,
            String name) {
    }

    public record CurrentUserResponse(
            String userId,
            String accountId,
            String name,
            String email,
            String timezone,
            String country,
            String role,
            String plan) {
    }
}
