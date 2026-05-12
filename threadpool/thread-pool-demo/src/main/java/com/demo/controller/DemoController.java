package com.demo.controller;

import com.demo.service.DemoService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final DemoService demoService;

    public DemoController(DemoService demoService) {
        this.demoService = demoService;
    }

    // Tomcat 스레드가 직접 처리 — DB 작업 완료될 때까지 해당 스레드 블로킹
    @PostMapping("/submit")
    public Map<String, Object> submit(
            @RequestParam(defaultValue = "500") int delay) {
        return demoService.processRequest(delay);
    }

    @PostMapping("/thread-pool-size")
    public Map<String, String> setThreadPoolSize(@RequestParam int size) {
        demoService.adjustTomcatThreadPoolSize(size);
        return Map.of("status", "ok");
    }

    @PostMapping("/db-pool-size")
    public Map<String, String> setDbPoolSize(@RequestParam int size) {
        demoService.adjustDbPoolSize(size);
        return Map.of("status", "ok");
    }

    @PostMapping("/reset")
    public Map<String, String> reset() {
        demoService.reset();
        return Map.of("status", "ok");
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return demoService.getStatus();
    }
}
