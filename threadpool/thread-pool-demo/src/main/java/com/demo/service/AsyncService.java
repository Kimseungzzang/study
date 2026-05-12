package com.demo.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static java.lang.Thread.sleep;

@Service
public class AsyncService {
    @Async("asyncExecutor")
    public CompletableFuture<Boolean> asyncMethod() throws InterruptedException {
        Thread.sleep(1000);
        return CompletableFuture.completedFuture(true);

    }
}
