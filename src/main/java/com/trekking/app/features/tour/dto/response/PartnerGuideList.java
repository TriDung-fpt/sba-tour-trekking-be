package com.trekking.app.features.tour.dto.response;

import java.util.List;

public record PartnerGuideList(List<Item> items) {
    public record Item(long id, long companyId, String displayName, boolean active) {}
}
