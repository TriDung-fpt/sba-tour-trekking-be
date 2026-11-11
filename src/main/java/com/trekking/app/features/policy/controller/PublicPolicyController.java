package com.trekking.app.features.policy.controller;

import com.trekking.app.features.policy.dto.response.PublicPolicyResponse;
import com.trekking.app.features.policy.service.PublicPolicyService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/policies")
public class PublicPolicyController {
    private final PublicPolicyService policies;

    public PublicPolicyController(PublicPolicyService policies) {
        this.policies = policies;
    }

    @GetMapping("/{code}")
    public ResponseEntity<PublicPolicyResponse> read(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(policies.read(code));
    }
}
