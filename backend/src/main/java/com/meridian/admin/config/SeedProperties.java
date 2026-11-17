package com.meridian.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.seed")
public record SeedProperties(boolean enabled) {
}
