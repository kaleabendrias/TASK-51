package com.booking.config;

import com.booking.util.FieldEncryptor;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EncryptionConfig {

    @Value("${app.encryption-key}")
    private String encryptionKey;

    @PostConstruct
    public void init() {
        FieldEncryptor.configure(encryptionKey);
    }
}
