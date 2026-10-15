package com.propbuy.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    private final boolean demoMode;
    HealthController(@Value("${app.demo-mode:false}") boolean demoMode) { this.demoMode = demoMode; }
    @GetMapping("/api/health")
    Map<String, Object> health() { return Map.of("status", "UP", "service", "propbuy", "demoMode", demoMode); }
}
