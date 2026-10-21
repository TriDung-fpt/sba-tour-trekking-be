package com.trekking.app.features.account.service.impl;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.account.dto.request.UpdateAccountRequest;
import com.trekking.app.features.account.dto.response.AccountResponse;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.service.AccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;

    public AccountServiceImpl(AppAccountRepository accounts, AccountRoleRepository roles) {
        this.accounts = accounts;
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getById(long accountId) {
        return response(accounts.findById(accountId).orElseThrow(AuthException::invalidSession));
    }

    @Override
    @Transactional
    public AccountResponse update(long accountId, UpdateAccountRequest request) {
        var account = accounts.findById(accountId).orElseThrow(AuthException::invalidSession);
        account.setFullName(request.fullName());
        account.setPhone(request.phone());
        accounts.flush();
        return response(account);
    }

    private AccountResponse response(com.trekking.app.features.account.entity.AppAccount account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getFullName(), account.getPhone(),
                roles.findAllByAccountId(account.getId()).stream().map(role -> role.getRole().getValue()).sorted().toList());
    }
}
