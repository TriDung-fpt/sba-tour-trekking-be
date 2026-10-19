package com.trekking.app.features.company.controller;

import com.trekking.app.features.company.dto.request.CompanyProfileRequest;
import com.trekking.app.features.company.dto.response.CompanyPageResponse;
import com.trekking.app.features.company.dto.response.CompanyResponse;
import com.trekking.app.features.company.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {
    private final CompanyService service;

    public CompanyController(CompanyService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public CompanyPageResponse list(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    public CompanyResponse read(@PathVariable long id) { return service.read(id); }

    @PutMapping("/{id}")
    public CompanyResponse update(@PathVariable long id, @Valid @RequestBody CompanyProfileRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/resubmit")
    public CompanyResponse resubmit(@PathVariable long id) { return service.resubmit(id); }

    @PostMapping(value = "/{id}/license", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyResponse upload(@PathVariable long id, @RequestParam("file") MultipartFile file) {
        return service.uploadLicense(id, file);
    }

    @GetMapping("/{id}/license")
    public ResponseEntity<byte[]> license(@PathVariable long id) {
        var evidence = service.license(id, false);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(evidence.fileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff").body(evidence.content());
    }
}
