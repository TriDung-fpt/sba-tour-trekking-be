package com.trekking.app.features.tour.service;

import com.trekking.app.features.tour.dto.response.PartnerDeparturePage;
import java.time.OffsetDateTime;

public interface PartnerDepartureService {
    PartnerDeparturePage list(long accountId, String status, OffsetDateTime from, OffsetDateTime to, int page, int size);
}
