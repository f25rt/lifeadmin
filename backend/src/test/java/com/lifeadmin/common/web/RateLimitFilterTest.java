package com.lifeadmin.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit test for the fixed-window rate limiter: requests up to the limit pass; the next is rejected
 * with 429 and a Retry-After header. Non-matching paths are never limited.
 */
class RateLimitFilterTest {

    // Mirror the app's mapper enough to serialize java.time types (JSR-310) in the 429 body.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RateLimitProperties props(final int authPerWindow) {
        final var p = new RateLimitProperties();
        p.setEnabled(true);
        p.setWindowSeconds(60);
        p.setAuthPerWindow(authPerWindow);
        p.setUploadPerWindow(authPerWindow);
        return p;
    }

    private MockHttpServletRequest authRequest() {
        final var req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.1");
        return req;
    }

    @Test
    void allowsUpToLimitThenReturns429() throws Exception {
        final var filter = new RateLimitFilter(props(3), objectMapper);

        for (int i = 0; i < 3; i++) {
            final var res = new MockHttpServletResponse();
            final var chain = new CountingChain();
            filter.doFilter(authRequest(), res, chain);
            assertThat(chain.called).as("request %s should pass", i).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }

        // 4th within the window is rejected.
        final var res = new MockHttpServletResponse();
        final var chain = new CountingChain();
        filter.doFilter(authRequest(), res, chain);
        assertThat(chain.called).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotNull();
        assertThat(res.getContentAsString()).contains("RATE_LIMITED");
    }

    @Test
    void doesNotLimitUnmatchedPaths() throws Exception {
        final var filter = new RateLimitFilter(props(1), objectMapper);
        final var req = new MockHttpServletRequest("GET", "/api/v1/documents");
        req.setRemoteAddr("10.0.0.9");

        for (int i = 0; i < 5; i++) {
            final var res = new MockHttpServletResponse();
            final var chain = new CountingChain();
            filter.doFilter(req, res, chain);
            assertThat(chain.called).isTrue();
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void disabledFilterNeverLimits() throws Exception {
        final var p = props(1);
        p.setEnabled(false);
        final var filter = new RateLimitFilter(p, objectMapper);

        for (int i = 0; i < 5; i++) {
            final var res = new MockHttpServletResponse();
            final var chain = new CountingChain();
            filter.doFilter(authRequest(), res, chain);
            assertThat(chain.called).isTrue();
        }
    }

    /** A FilterChain that records whether it was invoked (i.e. the request was allowed through). */
    private static final class CountingChain implements FilterChain {
        private boolean called;

        @Override
        public void doFilter(final jakarta.servlet.ServletRequest request,
                             final jakarta.servlet.ServletResponse response) {
            this.called = true;
            if (response instanceof MockHttpServletResponse res) {
                res.setStatus(200);
            }
        }
    }
}
