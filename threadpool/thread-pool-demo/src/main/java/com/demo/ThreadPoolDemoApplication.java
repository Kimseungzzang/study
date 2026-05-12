package com.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ThreadPoolDemoApplication {

    private final JdbcTemplate jdbcTemplate;

    public ThreadPoolDemoApplication(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(ThreadPoolDemoApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpPool() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }
}
