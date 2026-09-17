package com.lifeadmin.security.auth.api;

import com.lifeadmin.security.auth.AuthService;
import com.lifeadmin.security.auth.CurrentUser;
import com.lifeadmin.security.auth.api.AuthDtos.CurrentUserResponse;
import com.lifeadmin.security.auth.api.AuthDtos.LoginRequest;
import com.lifeadmin.security.auth.api.AuthDtos.LogoutRequest;
import com.lifeadmin.security.auth.api.AuthDtos.RefreshRequest;
import com.lifeadmin.security.auth.api.AuthDtos.RegisterRequest;
import com.lifeadmin.security.auth.api.AuthDtos.RegisterResponse;
import com.lifeadmin.security.auth.api.AuthDtos.TokenResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (API_SPEC §1). {@code /auth/register}, {@code /auth/login}, and
 * {@code /auth/refresh} are public; {@code /auth/logout} and {@code /auth/me} require a valid access
 * token.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody final RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody final LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody final RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody final LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me() {
        return ResponseEntity.ok(authService.currentUser(currentUser.require()));
    }
}
