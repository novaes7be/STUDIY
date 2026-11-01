package com.studiy.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoint público e sem dados, usado pelo front para saber se a API está no ar. */
@RestController
public class SaudeController {
    @GetMapping("/api/saude")
    public Map<String, String> saude() {
        return Map.of("status", "ok");
    }
}
