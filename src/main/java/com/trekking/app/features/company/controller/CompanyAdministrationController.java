package com.trekking.app.features.company.controller;

import com.trekking.app.features.company.dto.request.CompanyDecisionRequest;
import com.trekking.app.features.company.dto.response.CompanyPageResponse;
import com.trekking.app.features.company.dto.response.CompanyResponse;
import com.trekking.app.features.company.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/companies")
public class CompanyAdministrationController {
    private final CompanyService service;

    public CompanyAdministrationController(CompanyService service) { this.service = service; }

    @GetMapping
    public CompanyPageResponse list(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.reviewList(status, page, size);
    }

    @GetMapping("/{id}")
    public CompanyResponse read(@PathVariable long id) { return service.reviewRead(id); }

    @PostMapping("/{id}/decision")
    public CompanyResponse decide(@PathVariable long id, @Valid @RequestBody CompanyDecisionRequest request) {
        return service.decide(id, request);
    }

    @GetMapping("/{id}/license")
    public ResponseEntity<byte[]> license(@PathVariable long id) {
        var evidence = service.license(id, true);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(evidence.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(evidence.content());
    }
}
