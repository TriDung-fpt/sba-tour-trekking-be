package com.trekking.app.core.security;

import com.trekking.app.core.exception.AuthException;
import com.trekking.app.features.auth.validation.AuthValidation;
import com.trekking.app.features.auth.service.impl.SessionAccounts;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SessionJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final AuthValidation validation;
    private final SessionAccounts accounts;

    public SessionJwtAuthenticationConverter(AuthValidation validation, SessionAccounts accounts) {
        this.validation = validation;
        this.accounts = accounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        try {
            var session = validation.getSession(jwt.getClaimAsString("sid"));
            if (!Long.toString(session.accountId()).equals(jwt.getSubject())) throw AuthException.invalidSession();
            var response = accounts.response(accounts.validate(session));
            var authorities = response.roles().stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase(Locale.ROOT))).toList();
            return UsernamePasswordAuthenticationToken.authenticated(
                    new UserPrincipal(session.accountId(), session.id(), response), null, authorities);
        } catch (AuthException ex) {
            if (ex.getStatus() == HttpStatus.SERVICE_UNAVAILABLE) throw new AuthenticationServiceException("Auth unavailable");
            throw new BadCredentialsException("Session invalid");
        } catch (DataAccessException ex) {
            throw new AuthenticationServiceException("Auth unavailable");
        }
    }
}
