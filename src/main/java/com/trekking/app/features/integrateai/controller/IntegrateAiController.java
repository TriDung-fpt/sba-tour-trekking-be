package com.trekking.app.features.integrateai.controller;

import com.trekking.app.features.integrateai.dto.request.IntegrateAiChatRequest;
import com.trekking.app.features.integrateai.dto.response.IntegrateAiChatResponse;
import com.trekking.app.features.integrateai.service.IntegrateAiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/integrate-ai")
public class IntegrateAiController {
    private final IntegrateAiService service;
    public IntegrateAiController(IntegrateAiService service) { this.service = service; }

    @PostMapping("/chat")
    public ResponseEntity<IntegrateAiChatResponse> chat(@Valid @RequestBody IntegrateAiChatRequest request,
                                                         jakarta.servlet.http.HttpServletRequest http) {
        String key = http.getRemoteAddr() == null ? "unknown" : http.getRemoteAddr();
        return ResponseEntity.ok(service.chat(request.question(), request.sessionId(), key));
    }
}
