package com.lifeadmin.security.auth;

import java.time.Instant;

import com.lifeadmin.account.Account;
import com.lifeadmin.account.AccountRepository;
import com.lifeadmin.account.AppUser;
import com.lifeadmin.account.AppUserRepository;
import com.lifeadmin.account.Plan;
import com.lifeadmin.account.Subscription;
import com.lifeadmin.account.SubscriptionRepository;
import com.lifeadmin.account.UserRole;
import com.lifeadmin.common.error.ConflictException;
import com.lifeadmin.common.error.ResourceNotFoundException;
import com.lifeadmin.common.error.UnauthorizedException;
import com.lifeadmin.security.auth.api.AuthDtos.CurrentUserResponse;
import com.lifeadmin.security.auth.api.AuthDtos.RegisterRequest;
import com.lifeadmin.security.auth.api.AuthDtos.RegisterResponse;
import com.lifeadmin.security.auth.api.AuthDtos.TokenResponse;
import com.lifeadmin.security.jwt.JwtTokenService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication + registration.
 *
 * <p>Registration provisions the whole ownership tree in one transaction: an {@link Account}, its
 * owner {@link AppUser}, and a {@link Subscription} defaulted to {@link Plan#FREE} (D1/G3). Login
 * issues a short-lived access JWT plus a stateful refresh token; refresh rotates it; logout revokes
 * it (G18).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;

    @Transactional
    public RegisterResponse register(final RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with email '" + request.email() + "' already exists");
        }

        final var account = new Account();
        account.setName(request.name() + "'s account");
        final var savedAccount = accountRepository.save(account);

        final var user = new AppUser();
        user.setAccountId(savedAccount.getId());
        user.setEmail(request.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole(UserRole.OWNER);
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(request.timezone());
        }
        if (request.country() != null && !request.country().isBlank()) {
            user.setCountry(request.country());
        }
        final var savedUser = userRepository.save(user);

        final var subscription = new Subscription();
        subscription.setAccountId(savedAccount.getId());
        subscription.setPlan(Plan.FREE);
        subscriptionRepository.save(subscription);

        return new RegisterResponse(
                savedUser.getId().toString(), savedAccount.getId().toString(), savedUser.getEmail());
    }

    @Transactional
    public TokenResponse login(final String email, final String password) {
        final var user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }
        if (user.isDisabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse refresh(final String rawRefreshToken) {
        final var hash = tokenService.hashRefreshToken(rawRefreshToken);
        final var stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        final var now = Instant.now();
        if (!stored.isActive(now)) {
            throw new UnauthorizedException("Refresh token expired or revoked");
        }
        // Rotate: revoke the presented token, then issue a fresh pair.
        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);

        final var user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (user.isDisabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(final String rawRefreshToken) {
        final var hash = tokenService.hashRefreshToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(final LifeAdminPrincipal principal) {
        final var user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.userId()));
        final var plan = subscriptionRepository.findByAccountId(user.getAccountId())
                .map(s -> s.getPlan().name())
                .orElse(Plan.FREE.name());
        return new CurrentUserResponse(
                user.getId().toString(), user.getAccountId().toString(), user.getName(), user.getEmail(),
                user.getTimezone(), user.getCountry(), user.getRole().name(), plan);
    }

    // --- helpers ---

    private TokenResponse issueTokens(final AppUser user) {
        final var accessToken = tokenService.createAccessToken(
                user.getId(), user.getAccountId(), user.getEmail(), user.getRole().name());

        final var rawRefresh = tokenService.generateRefreshTokenValue();
        final var refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(tokenService.hashRefreshToken(rawRefresh));
        refreshToken.setExpiresAt(tokenService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(
                accessToken, rawRefresh, "Bearer", tokenService.getAccessTtlSeconds(),
                user.getId().toString(), user.getAccountId().toString(), user.getName());
    }
}
