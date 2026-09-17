package com.lifeadmin.provider.ocr;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link OcrProperties} binding. */
@Configuration
@EnableConfigurationProperties(OcrProperties.class)
public class OcrConfig {
}
