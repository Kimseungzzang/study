package com.demo.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StatusBroadcaster {

    private final DemoService demoService;
    private final SimpMessagingTemplate messaging;

    public StatusBroadcaster(DemoService demoService, SimpMessagingTemplate messaging) {
        this.demoService = demoService;
        this.messaging = messaging;
    }

    @Scheduled(fixedRate = 250)
    public void broadcast() {
        messaging.convertAndSend("/topic/status", demoService.getStatus());
    }
}
