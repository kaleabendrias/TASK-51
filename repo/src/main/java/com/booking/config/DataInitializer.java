package com.booking.config;

import com.booking.domain.User;
import com.booking.mapper.UserMapper;
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
        int updated = 0;
        for (User user : users) {
            if (PLACEHOLDER.equals(user.getPasswordHash())) {
                String hash = passwordEncoder.encode(DEFAULT_PASSWORD);
                userMapper.updatePasswordHash(user.getId(), hash);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Initialized passwords for {} seed users", updated);
        }
    }
}
