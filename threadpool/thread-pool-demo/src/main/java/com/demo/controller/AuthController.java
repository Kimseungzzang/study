package com.demo.controller;

import com.demo.service.AsyncService;
import com.demo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AsyncService asyncService;

    public AuthController(AuthService authService, AsyncService asyncService) {
        this.authService = authService;
        this.asyncService=asyncService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        boolean success = authService.login(request.username(), request.password());
        if (success) {
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "username", request.username(),
                "thread", Thread.currentThread().getName()
            ));
        }
        return ResponseEntity.status(401).body(Map.of("status", "failed"));
    }
    @PostMapping("/async")
    public ResponseEntity<String> asyncEndPoint() throws InterruptedException {
        asyncService.asyncMethod();
        return ResponseEntity.status(200).body("OK");
    }

    public record LoginRequest(String username, String password) {}
}
