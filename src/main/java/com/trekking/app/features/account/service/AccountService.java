package com.trekking.app.features.account.service;

import com.trekking.app.features.account.dto.request.UpdateAccountRequest;
import com.trekking.app.features.account.dto.response.AccountResponse;

public interface AccountService {
    AccountResponse getById(long accountId);
    AccountResponse update(long accountId, UpdateAccountRequest request);
}
