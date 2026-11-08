package com.trekking.app.features.tour.dto.response;

import java.time.OffsetDateTime;

public record HandoverPhotoUploadResponse(
        String id,
        String url,
        String storageKey,
        String fileName,
        OffsetDateTime capturedAt
) {}
