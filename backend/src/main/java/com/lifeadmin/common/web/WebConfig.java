package com.lifeadmin.common.web;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies the {@code /api/v1} prefix to every {@link RestController} mapping.
 *
 * <p>Using a controller path-prefix (rather than {@code server.servlet.context-path}) keeps the API
 * base path identical everywhere it is matched — controller mappings, Spring Security request
 * matchers, and MockMvc — avoiding the context-path stripping inconsistencies between the embedded
 * container and the test dispatcher.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(final PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, c -> c.isAnnotationPresent(RestController.class));
    }
}
