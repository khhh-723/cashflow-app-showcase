package com.cashflow.server.config;

import com.cashflow.server.model.entity.User;
import com.cashflow.server.repository.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Creates a deterministic local test account for Android/manual integration testing.
 *
 * Disabled when the Spring "prod" profile is active. The password is intentionally
 * reset on startup so local demo credentials stay predictable during development.
 */
@Component
@Profile("!prod")
public class DevTestUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevTestUserInitializer.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String username;
    private final String password;

    public DevTestUserInitializer(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            @Value("${app.dev.seed-test-user.enabled:true}") boolean enabled,
            @Value("${app.dev.seed-test-user.email:demo@suishouji.local}") String email,
            @Value("${app.dev.seed-test-user.username:demo}") String username,
            @Value("${app.dev.seed-test-user.password:Demo@123456}") String password) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }

        var existingByEmail = userMapper.findByEmail(email);
        if (existingByEmail.isPresent()) {
            User user = existingByEmail.orElseThrow();
            user.setUsername(username);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            log.info("Local test account refreshed: {}", email);
            return;
        }

        if (userMapper.findByUsername(username).isPresent()) {
            log.warn("Skipped local test account seed because username '{}' already exists with another email", username);
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        log.info("Local test account created: {}", email);
    }
}
