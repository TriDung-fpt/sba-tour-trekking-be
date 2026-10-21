package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record ReviewQueueItem(long id, long companyId, String title, OffsetDateTime submittedAt,
                                  long submittedByAccountId, int mediaCount, int itineraryDays) {}
