
package com.example.demo.config;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.admin.reset-password",
        havingValue = "true")
public class PasswordInitializer implements CommandLineRunner {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.initial-password:}")
    private String initialPassword;

    @Override
    public void run(String... args) {

        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException(
                    "Admin initial password is not configured");
        }

        // Find admin user in Catalyst Data Store
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Admin user not found in Catalyst: "
                                + adminUsername));

        // Generate BCrypt password hash
        String encodedPassword =
                passwordEncoder.encode(initialPassword);

        // Update password directly in Catalyst
        userRepository.updatePasswordHash(
                admin.getId(), encodedPassword);

        System.out.println(
                "Admin password initializer completed successfully.");
    }
}
