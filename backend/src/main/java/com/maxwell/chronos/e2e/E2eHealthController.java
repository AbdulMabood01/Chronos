package com.maxwell.chronos.e2e;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("e2e")
public class E2eHealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "profile", "e2e");
    }
}
