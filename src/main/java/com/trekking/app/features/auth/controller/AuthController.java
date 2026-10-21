package com.trekking.app.features.auth.controller;

import com.trekking.app.core.config.AuthProperties;
import com.trekking.app.core.exception.AuthException;
import com.trekking.app.core.security.UserPrincipal;
import com.trekking.app.features.auth.dto.request.AuthRequests;
import com.trekking.app.features.account.dto.response.AccountResponse;
import com.trekking.app.features.auth.dto.response.AuthResponse;
import com.trekking.app.features.auth.dto.response.CsrfResponse;
import com.trekking.app.features.auth.dto.response.MessageResponse;
import com.trekking.app.features.auth.service.AuthService;
import com.trekking.app.features.account.dto.request.UpdateAccountRequest;
import com.trekking.app.features.account.service.AccountService;
import com.trekking.app.features.auth.model.IssuedTokens;
import com.trekking.app.features.auth.enums.OtpPurpose;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "trekking_refresh";
    private static final MessageResponse GENERIC = new MessageResponse("If the account is eligible, a verification code will be sent");
    private final AuthService service;
    private final AuthProperties properties;
    private final Clock clock;
    private final AccountService accounts;

    public AuthController(AuthService service, AuthProperties properties, Clock clock, AccountService accounts) {
        this.service = service;
        this.properties = properties;
        this.clock = clock;
        this.accounts = accounts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody AuthRequests.Register request) {
        service.register(request);
        return new MessageResponse("Account created; verify your email before logging in");
    }

    @PostMapping("/verify-email")
    public MessageResponse verify(@Valid @RequestBody AuthRequests.VerifyEmail request) {
        service.verifyEmail(request);
        return new MessageResponse("Email verified");
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resend(@Valid @RequestBody AuthRequests.EmailRequest request) {
        service.requestOtp(request.email(), OtpPurpose.VERIFY_EMAIL);
        return GENERIC;
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgot(@Valid @RequestBody AuthRequests.EmailRequest request) {
        service.requestOtp(request.email(), OtpPurpose.RESET_PASSWORD);
        return GENERIC;
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody AuthRequests.ResetPassword request, HttpServletResponse response) {
        service.resetPassword(request);
        cookie(response, "", Duration.ZERO);
        return new MessageResponse("Password reset; please log in again");
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequests.Login request, HttpServletRequest http,
                              HttpServletResponse response) {
        return tokens(response, service.login(request, http.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh,
                                HttpServletResponse response) {
        try { return tokens(response, service.refresh(refresh)); }
        catch (AuthException ex) {
            if (ex.getStatus() == HttpStatus.UNAUTHORIZED) cookie(response, "", Duration.ZERO);
            throw ex;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refresh, HttpServletResponse response) {
        cookie(response, "", Duration.ZERO);
        service.logout(refresh);
    }

    @GetMapping("/me")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.Operation(summary = "Get the authenticated account profile")
    public AccountResponse me(@AuthenticationPrincipal UserPrincipal principal) { return accounts.getById(principal.accountId()); }

    @PutMapping("/me")
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.Operation(summary = "Update the authenticated account profile",
            description = "Updates full name and phone. Email and roles are read-only.")
    public AccountResponse updateMe(@AuthenticationPrincipal UserPrincipal principal,
                                    @Valid @RequestBody UpdateAccountRequest request) {
        return accounts.update(principal.accountId(), request);
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(@io.swagger.v3.oas.annotations.Parameter(hidden = true) CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    private AuthResponse tokens(HttpServletResponse response, IssuedTokens tokens) {
        cookie(response, tokens.refreshToken(), Duration.between(clock.instant(), tokens.sessionExpiresAt()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return tokens.response();
    }

    private void cookie(HttpServletResponse response, String value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true)
                .secure(properties.cookieSecure()).sameSite("Lax").path("/api/v1/auth")
                .maxAge(ttl.isNegative() ? Duration.ZERO : ttl).build().toString());
    }
}
