package com.igot.cb.health.controller;

import com.igot.cb.health.service.HealthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.igot.cb.util.ApiResponse;


@RestController
public class HealthController {

    @Autowired
    private HealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse> healthCheck() throws Exception {
        ApiResponse response = healthService.checkHealthStatus();
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
