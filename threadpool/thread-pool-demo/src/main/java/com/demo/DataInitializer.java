package com.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String hash = encoder.encode("password123");

        for (int i = 1; i <= 20; i++) {
            jdbcTemplate.update(
                "INSERT INTO users (username, password) VALUES (?, ?) ON CONFLICT DO NOTHING",
                "user" + i, hash
            );
        }
        System.out.println("[DataInitializer] 테스트 유저 20명 준비 완료 (password: password123)");
    }
}
