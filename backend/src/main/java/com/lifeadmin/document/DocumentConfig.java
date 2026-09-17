package com.lifeadmin.document;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link UploadProperties} binding. */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class DocumentConfig {
}
