package com.lifeadmin.security;

import java.util.Optional;

import com.lifeadmin.security.auth.CurrentUser;
import com.lifeadmin.security.jwt.JwtAuthenticationFilter;
import com.lifeadmin.security.jwt.JwtTokenService;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. Public endpoints: auth (register/login/refresh), health, and API docs.
 * Everything else requires a valid access token. Method security is enabled for future
 * {@code @PreAuthorize} use. Provides the {@link PasswordEncoder} and the {@link AuditorAware} bean
 * that populates {@code createdBy}/{@code updatedBy} from the current principal.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(com.lifeadmin.security.jwt.JwtProperties.class)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(final JwtTokenService tokenService) {
        return new JwtAuthenticationFilter(tokenService);
    }

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http, final JwtAuthenticationFilter jwtFilter)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints. The /api/v1 prefix is applied to @RestControllers via
                        // WebConfig, so auth paths are matched with that prefix. Actuator and
                        // springdoc are not @RestControllers and keep their own (unprefixed) paths.
                        .requestMatchers(
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Populates {@code @CreatedBy}/{@code @LastModifiedBy} with the current user's email (or "system"). */
    @Bean
    public AuditorAware<String> auditorAware(final CurrentUser currentUser) {
        return () -> Optional.of(currentUser.principal().map(p -> p.email()).orElse("system"));
    }
}
