package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.CreateTourCodeRequest;
import com.trekking.app.features.tour.dto.request.DeleteTourCodeRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourCodeRequest;
import com.trekking.app.features.tour.dto.response.TourCodeLogResponse;
import com.trekking.app.features.tour.dto.response.TourCodeResponse;
import com.trekking.app.features.tour.service.TourCodeService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tour-codes")
@SecurityRequirement(name = "bearerAuth")
public class TourCodeController {
    private final TourCodeService codes;

    public TourCodeController(TourCodeService codes) {
        this.codes = codes;
    }

    @GetMapping
    public Page<TourCodeResponse> list(@RequestParam(required = false) Long tourId,
                                       @RequestParam(defaultValue = "false") boolean includeDeleted,
                                       @RequestParam(required = false) String keyword,
                                       @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                       Pageable pageable) {
        return codes.list(tourId, includeDeleted, keyword, pageable);
    }

    @GetMapping("/{id}")
    public TourCodeResponse get(@PathVariable long id) {
        return codes.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TourCodeResponse create(@Valid @RequestBody CreateTourCodeRequest request) {
        return codes.create(request);
    }

    @PutMapping("/{id}")
    public TourCodeResponse rename(@PathVariable long id, @Valid @RequestBody UpdateTourCodeRequest request) {
        return codes.rename(id, request);
    }

    @DeleteMapping("/{id}")
    public TourCodeResponse delete(@PathVariable long id, @Valid @RequestBody DeleteTourCodeRequest request) {
        return codes.delete(id, request);
    }

    @GetMapping("/tours/{tourId}/logs")
    public List<TourCodeLogResponse> logs(@PathVariable long tourId) {
        return codes.logs(tourId);
    }
}
