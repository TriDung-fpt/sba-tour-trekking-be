package com.trekking.app.features.account.service.account;

import com.trekking.app.features.account.dto.response.AccountResponse;
import com.trekking.app.features.account.entity.AppAccount;
import com.trekking.app.features.account.repository.AccountRoleRepository;
import com.trekking.app.features.account.repository.AppAccountRepository;
import com.trekking.app.features.account.validation.AuthValidation;

import com.trekking.app.features.account.service.session.AuthSession;
import org.springframework.stereotype.Service;

@Service
public class SessionAccounts {
    private final AppAccountRepository accounts;
    private final AccountRoleRepository roles;
    private final AuthValidation validation;

    public SessionAccounts(AppAccountRepository accounts, AccountRoleRepository roles, AuthValidation validation) {
        this.accounts = accounts;
        this.roles = roles;
        this.validation = validation;
    }

    public AppAccount validate(AuthSession session) {
        AppAccount account = accounts.findById(session.accountId()).orElse(null);
        validation.validateSession(session, account);
        return account;
    }

    public AccountResponse response(AppAccount account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getFullName(), account.getPhone(),
                roles.findAllByAccountId(account.getId()).stream().map(r -> r.getRole().getValue()).sorted().toList());
    }
}
