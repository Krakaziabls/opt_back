package com.example.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SockJSInfoController {

    @GetMapping("/ws/info")
    public ResponseEntity<String> sockJSInfo() {
        return ResponseEntity.ok("{\"websocket\":true}");
    }
}
