package org.bobj.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.bobj.common.response.ApiCommonResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthCheckController {

    @GetMapping("/health")
    public ResponseEntity<ApiCommonResponse<Map<String, Object>>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(ApiCommonResponse.createSuccess(data));
    }
}
