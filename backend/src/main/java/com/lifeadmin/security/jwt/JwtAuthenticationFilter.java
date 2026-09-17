package com.lifeadmin.security.jwt;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.lifeadmin.security.auth.LifeAdminPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Extracts and validates a Bearer access token on each request. On success, populates the security
 * context with a {@link LifeAdminPrincipal} and the user's role authority. Invalid/expired tokens
 * are ignored here (the request proceeds unauthenticated); the security config decides whether the
 * target endpoint requires authentication.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(final JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain) throws ServletException, IOException {

        final var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            final var token = header.substring(7);
            try {
                final var claims = tokenService.parse(token);
                final var userId = UUID.fromString(claims.getSubject());
                final var accountId = UUID.fromString(claims.get("accountId", String.class));
                final var email = claims.get("email", String.class);
                final var role = claims.get("role", String.class);

                final var principal = new LifeAdminPrincipal(userId, accountId, email, role);
                final var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                final var authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (final Exception ex) {
                // Invalid/expired token -> leave the context unauthenticated.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
