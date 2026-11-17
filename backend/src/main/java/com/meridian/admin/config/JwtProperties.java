package com.meridian.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.jwt")
public record JwtProperties(String secret, int expirationMinutes) {
}
