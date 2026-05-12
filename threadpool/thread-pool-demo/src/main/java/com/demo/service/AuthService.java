package com.demo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // BCrypt 검증이 약 100~300ms 소요 → 스레드 자연스럽게 점유
    public boolean login(String username, String password) {
        try {
            String stored = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE username = ?",
                String.class, username
            );
            return encoder.matches(password, stored);
        } catch (Exception e) {
            return false;
        }
    }

}
