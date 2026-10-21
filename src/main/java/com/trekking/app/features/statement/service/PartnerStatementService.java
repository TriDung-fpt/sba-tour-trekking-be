package com.trekking.app.features.statement.service;

import com.trekking.app.features.statement.dto.response.PartnerStatementPage;

public interface PartnerStatementService {
    PartnerStatementPage list(long accountId, int page, int size);
}
