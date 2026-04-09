package com.booking.config;

import com.booking.domain.User;
import com.booking.mapper.UserMapper;
import com.booking.util.FieldEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String PLACEHOLDER = "$PLACEHOLDER$";
    private static final String DEFAULT_PASSWORD = "password123";

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public DataInitializer(UserMapper userMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        List<User> users = userMapper.findAll();
        int pwdUpdated = 0;
        int phoneEncrypted = 0;
        for (User user : users) {
            if (PLACEHOLDER.equals(user.getPasswordHash())) {
                userMapper.updatePasswordHash(user.getId(), passwordEncoder.encode(DEFAULT_PASSWORD));
                pwdUpdated++;
            }
            // Encrypt plaintext phone numbers at rest
            String phone = user.getPhone();
            if (phone != null && !phone.isEmpty() && !isEncrypted(phone)) {
                userMapper.updatePhone(user.getId(), FieldEncryptor.encrypt(phone));
                phoneEncrypted++;
            }
        }
        if (pwdUpdated > 0) log.info("Initialized passwords for {} seed users", pwdUpdated);
        if (phoneEncrypted > 0) log.info("Encrypted phone numbers for {} users", phoneEncrypted);
    }

    private boolean isEncrypted(String value) {
        // Encrypted values are Base64-encoded and significantly longer than plain phone numbers
        try {
            FieldEncryptor.decrypt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
