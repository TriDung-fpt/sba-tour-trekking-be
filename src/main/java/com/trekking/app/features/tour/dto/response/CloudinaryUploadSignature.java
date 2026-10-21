package com.trekking.app.features.tour.dto.response;

public record CloudinaryUploadSignature(String cloudName, String apiKey, long timestamp,
                                        String folder, String allowedFormats, String signature) {}
