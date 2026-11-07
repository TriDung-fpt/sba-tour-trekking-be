package com.trekking.app.features.account.dto.response;

import java.util.List;

public record AdminAccountPageResponse(List<AdminAccountListItemResponse> items, int page, int size,
                                       long totalElements, int totalPages) {
    public AdminAccountPageResponse { items = List.copyOf(items); }
}
