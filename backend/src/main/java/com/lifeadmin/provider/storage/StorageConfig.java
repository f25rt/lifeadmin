package com.lifeadmin.provider.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables {@link StorageProperties} binding. */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {
}
