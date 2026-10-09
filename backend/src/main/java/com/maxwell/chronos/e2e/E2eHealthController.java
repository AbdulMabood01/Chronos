package com.maxwell.chronos.e2e;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("e2e")
public class E2eHealthController {
    private volatile boolean ready;

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void ready() { ready = true; }

    @GetMapping("/health")
    public org.springframework.http.ResponseEntity<Map<String, String>> health() {
        return org.springframework.http.ResponseEntity.status(ready ? 200 : 503)
                .body(Map.of("status", ready ? "UP" : "STARTING", "profile", "e2e"));
    }
}
