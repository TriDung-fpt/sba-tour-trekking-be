package com.trekking.app.features.payment.service;

import com.trekking.app.features.payment.dto.request.SaveRefundRulesRequest;
import com.trekking.app.features.payment.dto.response.RefundRulesResponse;
import java.time.OffsetDateTime;

public interface RefundRulesService {
    RefundRulesResponse current();
    RefundRulesResponse save(SaveRefundRulesRequest request);
    String snapshotForBooking(OffsetDateTime at);
}
