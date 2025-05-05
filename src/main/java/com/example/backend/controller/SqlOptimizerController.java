package com.example.backend.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sql")
public class SqlOptimizerController {
    @PostMapping("/optimize")
    public Map<String, String> optimizeSql(@RequestBody Map<String, String> request) {
        return Map.of(
                "formattedSql", request.get("sql"),
                "plan", "Mock plan",
                "recommendations", "Mock recommendations"
        );
    }
}
