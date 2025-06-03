package com.example.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ws")
public class SockJSInfoController {

    @GetMapping("/info")
    public ResponseEntity<String> sockJSInfo() {
        return ResponseEntity.ok("{\"websocket\":true}");
    }
}
