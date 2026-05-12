package com.demo.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DemoService {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final SimpMessagingTemplate messaging;
    private final ApplicationContext applicationContext;

    private final ConcurrentHashMap<Long, Map<String, Object>> activeTasks = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    private final AtomicLong completedCount = new AtomicLong(0);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public DemoService(JdbcTemplate jdbcTemplate,
                       DataSource dataSource,
                       SimpMessagingTemplate messaging,
                       ApplicationContext applicationContext) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
        this.messaging = messaging;
        this.applicationContext = applicationContext;
    }

    private org.apache.tomcat.util.threads.ThreadPoolExecutor getTomcatExecutor() {
        ServletWebServerApplicationContext ctx = (ServletWebServerApplicationContext) applicationContext;
        TomcatWebServer webServer = (TomcatWebServer) ctx.getWebServer();
        for (Connector connector : webServer.getTomcat().getService().findConnectors()) {
            java.util.concurrent.Executor executor = connector.getProtocolHandler().getExecutor();
            if (executor instanceof org.apache.tomcat.util.threads.ThreadPoolExecutor tpe) {
                return tpe;
            }
        }
        return null;
    }

    public Map<String, Object> processRequest(int delayMs) {
        String threadName = Thread.currentThread().getName();
        long taskId = taskIdCounter.incrementAndGet();

        Map<String, Object> info = new HashMap<>();
        info.put("id", taskId);
        info.put("thread", threadName);
        info.put("startTime", System.currentTimeMillis());
        info.put("delay", delayMs);
        activeTasks.put(taskId, info);

        log(threadName + " → req#" + taskId + " 시작", "info");
        try {
            jdbcTemplate.execute((Connection conn) -> {
                try (PreparedStatement ps = conn.prepareStatement("SELECT pg_sleep(?)")) {
                    ps.setDouble(1, delayMs / 1000.0);
                    ps.execute();
                }
                return null;
            });
            log(threadName + " → req#" + taskId + " 완료", "success");
        } catch (Exception e) {
            log(threadName + " → req#" + taskId + " DB오류: " + e.getMessage(), "error");
        } finally {
            activeTasks.remove(taskId);
            completedCount.incrementAndGet();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("taskId", taskId);
        result.put("thread", threadName);
        return result;
    }

    public void adjustTomcatThreadPoolSize(int size) {
        int s = Math.max(1, Math.min(200, size));
        try {
            org.apache.tomcat.util.threads.ThreadPoolExecutor exec = getTomcatExecutor();
            if (exec == null) {
                log("Tomcat Executor를 찾을 수 없음", "error");
                return;
            }
            // core <= max 조건 유지
            // 줄일 때: core 먼저, 늘릴 때: max 먼저
            if (s < exec.getCorePoolSize()) {
                exec.setCorePoolSize(s);
                exec.setMaximumPoolSize(s);
            } else {
                exec.setMaximumPoolSize(s);
                exec.setCorePoolSize(s);
            }
            log("Tomcat 스레드 풀 크기 변경 → " + s, "info");
        } catch (Exception e) {
            log("스레드 풀 크기 변경 실패: " + e.getMessage(), "error");
        }
    }

    public void adjustDbPoolSize(int size) {
        int s = Math.max(1, Math.min(20, size));
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.setMaximumPoolSize(s);
            log("DB 커넥션 풀 크기 변경 → " + s, "info");
        }
    }

    public void reset() {
        activeTasks.clear();
        completedCount.set(0);
        taskIdCounter.set(0);
        log("전체 리셋 완료", "info");
    }

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();

        // Tomcat 스레드 풀 상태
        Map<String, Object> threadPool = new HashMap<>();
        try {
            org.apache.tomcat.util.threads.ThreadPoolExecutor exec = getTomcatExecutor();
            if (exec != null) {
                int maxThreads = exec.getMaximumPoolSize();
                int activeCount = exec.getActiveCount();
                int queueSize = exec.getQueue().size();

                List<Map<String, Object>> slots = new ArrayList<>();
                for (int i = 0; i < maxThreads; i++) {
                    Map<String, Object> slot = new HashMap<>();
                    slot.put("id", i + 1);
                    slot.put("state", i < activeCount ? "BUSY" : "IDLE");
                    slots.add(slot);
                }

                threadPool.put("poolSize", maxThreads);
                threadPool.put("activeCount", activeCount);
                threadPool.put("completedCount", completedCount.get());
                threadPool.put("queueSize", queueSize);
                threadPool.put("slots", slots);
            }
        } catch (Exception e) {
            threadPool.put("poolSize", 5);
            threadPool.put("activeCount", 0);
            threadPool.put("completedCount", completedCount.get());
            threadPool.put("queueSize", 0);
            threadPool.put("slots", Collections.emptyList());
        }
        status.put("threadPool", threadPool);

        // DB 커넥션 풀 (HikariCP)
        Map<String, Object> dbPool = new HashMap<>();
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                int maxSize = hikari.getMaximumPoolSize();
                int activeConn = pool.getActiveConnections();
                int idleConn = pool.getIdleConnections();
                int waiting = pool.getThreadsAwaitingConnection();

                List<Map<String, Object>> connSlots = new ArrayList<>();
                for (int i = 0; i < maxSize; i++) {
                    Map<String, Object> slot = new HashMap<>();
                    slot.put("id", i + 1);
                    slot.put("state", i < activeConn ? "BUSY" : "IDLE");
                    connSlots.add(slot);
                }

                dbPool.put("maxSize", maxSize);
                dbPool.put("activeConnections", activeConn);
                dbPool.put("idleConnections", idleConn);
                dbPool.put("threadsAwaiting", waiting);
                dbPool.put("slots", connSlots);
            }
        }
        status.put("dbPool", dbPool);

        return status;
    }

    private void log(String message, String level) {
        String time = LocalDateTime.now().format(TIME_FMT);
        Map<String, String> entry = new HashMap<>();
        entry.put("time", time);
        entry.put("message", message);
        entry.put("level", level);
        messaging.convertAndSend("/topic/log", entry);
    }
}
