package com.trekking.app.features.statement.dto.response;

import java.util.List;

public record AdminCompanyPage(List<Item> items, int page, int size, long totalItems, int totalPages) {
    public record Item(long id, String name, long eligibleBookingCount) {}
}
