package com.lifeadmin.common.web;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeadmin.common.error.ApiError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, in-memory rate limiter for the abuse-prone endpoints (Phase 5 hardening, G4):
 * authentication (login/register/refresh — credential stuffing) and upload/process (expensive
 * OCR/AI). Keyed by client IP + bucket; over-limit requests get {@code 429} with the standard error
 * envelope and a {@code Retry-After} header.
 *
 * <p>Dependency-free and per-instance by design — adequate for the single-instance MVP. A
 * distributed deployment should move this to a shared store (e.g. Redis) so the window is global;
 * noted in the deployment README. Runs just after the correlation-id filter so 429s are traceable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API = WebConfig.API_PREFIX;

    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    /** key -> (windowStartEpochSecond, count). */
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(final RateLimitProperties props, final ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull final HttpServletRequest request) {
        return !props.isEnabled() || bucketFor(request) == null;
    }

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain) throws ServletException, IOException {

        final var bucket = bucketFor(request);
        // bucket is non-null here (shouldNotFilter guards otherwise).
        final int limit = "auth".equals(bucket) ? props.getAuthPerWindow() : props.getUploadPerWindow();
        final long nowSec = Instant.now().getEpochSecond();
        final long windowStart = nowSec - (nowSec % props.getWindowSeconds());
        final var key = bucket + '|' + clientIp(request);

        final var window = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.start != windowStart) {
                return new Window(windowStart);
            }
            return existing;
        });

        if (window.count.incrementAndGet() > limit) {
            writeTooManyRequests(request, response, windowStart + props.getWindowSeconds() - nowSec);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Which rate bucket a request falls into, or {@code null} if it isn't rate-limited. */
    private String bucketFor(final HttpServletRequest request) {
        final var path = request.getRequestURI();
        final var method = request.getMethod();
        if (path.startsWith(API + "/auth/")) {
            return "auth";
        }
        // Upload (POST /documents) and reprocess (POST /documents/{id}/process).
        if ("POST".equalsIgnoreCase(method)
                && (path.equals(API + "/documents") || path.endsWith("/process"))
                && path.startsWith(API + "/documents")) {
            return "upload";
        }
        return null;
    }

    private static String clientIp(final HttpServletRequest request) {
        final var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First hop is the original client when behind a trusted proxy.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(
            final HttpServletRequest request, final HttpServletResponse response, final long retryAfterSec)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfterSec)));
        final var body = new ApiError(
                Instant.now(), HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMITED",
                "Too many requests. Please slow down and try again shortly.",
                request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** A fixed window: its start second and a request counter. */
    private static final class Window {
        private final long start;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(final long start) {
            this.start = start;
        }
    }
}
