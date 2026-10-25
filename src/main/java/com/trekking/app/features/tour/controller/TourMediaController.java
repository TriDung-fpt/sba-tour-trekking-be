package com.trekking.app.features.tour.controller;

import com.trekking.app.features.tour.dto.request.ReorderTourMediaRequest;
import com.trekking.app.features.tour.dto.request.UpdateTourMediaRequest;
import com.trekking.app.features.tour.dto.request.UploadTourMediaRequest;
import com.trekking.app.features.tour.dto.response.TourMediaResponse;
import com.trekking.app.features.tour.service.TourMediaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/tours/{tourId}/media")
@SecurityRequirement(name = "bearerAuth")
public class TourMediaController {
    private final TourMediaService media;

    public TourMediaController(TourMediaService media) {
        this.media = media;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TourMediaResponse upload(@PathVariable long tourId, @RequestPart("file") MultipartFile file,
                                    @RequestParam(required = false) String caption) {
        return media.upload(tourId, new UploadTourMediaRequest(file, caption));
    }

    @PatchMapping("/{mediaId}")
    public TourMediaResponse updateCaption(@PathVariable long tourId, @PathVariable long mediaId,
                                           @RequestBody UpdateTourMediaRequest request) {
        return media.updateCaption(tourId, mediaId, request);
    }

    @PatchMapping("/{mediaId}/cover")
    public TourMediaResponse setCover(@PathVariable long tourId, @PathVariable long mediaId) {
        return media.setCover(tourId, mediaId);
    }

    @PutMapping("/order")
    public List<TourMediaResponse> reorder(@PathVariable long tourId,
                                          @Valid @RequestBody ReorderTourMediaRequest request) {
        return media.reorder(tourId, request);
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long tourId, @PathVariable long mediaId) {
        media.delete(tourId, mediaId);
    }
}
