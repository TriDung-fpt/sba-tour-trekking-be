package com.trekking.app.features.tour.dto.response;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record CancellationPolicyVersion(long id, String policyTitle, int versionNumber,
                                            String content, OffsetDateTime effectiveFrom) {}
