package com.order.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/print")
public class PrintTestController {

    @GetMapping("/data")
    public Map<String, String> getPrintData() {
        // 動的に生成してもいいけど、まずはテスト用に固定
        return Map.of("text", "Hello from Spring Boot!");
    }
}
