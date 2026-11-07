package com.trekking.app.features.dashboard.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record TopToursResponse(
        String basis,
        OffsetDateTime from,
        OffsetDateTime to,
        List<Row> tours) {

    public record Row(
            long tourId,
            String tourName,
            long bookingCount,
            long passengerCount,
            Double share) {
    }
}
