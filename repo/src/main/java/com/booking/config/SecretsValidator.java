package com.booking.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-fast validation of critical runtime secrets.
 *
 * Runs during Spring context initialization (before any database
 * connection or business logic).  If any secret is missing or below
 * the required strength the application refuses to start with an
 * explicit, human-readable error rather than a cryptic connection
 * failure or encryption exception downstream.
 */
@Configuration
public class SecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(SecretsValidator.class);
    private static final int MIN_ENCRYPTION_KEY_LENGTH = 32;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${app.encryption-key:}")
    private String encryptionKey;

    @PostConstruct
    public void validate() {
        if (datasourceUsername == null || datasourceUsername.isBlank()) {
            throw new IllegalStateException(
                    "SPRING_DATASOURCE_USERNAME is not set. "
                    + "Database credentials must be provided via environment variables.");
        }

        if (datasourcePassword == null || datasourcePassword.isBlank()) {
            throw new IllegalStateException(
                    "SPRING_DATASOURCE_PASSWORD is not set. "
                    + "Database credentials must be provided via environment variables.");
        }

        if (encryptionKey == null || encryptionKey.isBlank()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY is not set. "
                    + "A cryptographic key of at least " + MIN_ENCRYPTION_KEY_LENGTH
                    + " characters must be provided via environment variables.");
        }

        if (encryptionKey.length() < MIN_ENCRYPTION_KEY_LENGTH) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY is " + encryptionKey.length()
                    + " characters but must be at least " + MIN_ENCRYPTION_KEY_LENGTH
                    + " for AES-256. Provide a stronger key.");
        }

        log.info("Secrets validation passed — datasource credentials and encryption key are present and meet strength requirements.");
    }
}
