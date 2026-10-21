package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record Review(long id, short tourRating, short companyRating, String content,
                         List<String> imageUrls, OffsetDateTime createdAt, String companyReply) {}
