package com.trekking.app.features.tour.dto.request;

import org.springframework.web.multipart.MultipartFile;

public record UploadTourMediaRequest(MultipartFile file, String caption) {}
