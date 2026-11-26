package com.cryptotrading.security.presentation;

import com.cryptotrading.security.application.AuthenticationFailedException;
import com.cryptotrading.security.application.UserAccount;
import com.cryptotrading.security.application.UserAccountService;
import com.cryptotrading.security.infrastructure.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=<redacted>]";
        }
    }

    record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserAccount user) {
    }

    record TotpCode(@NotBlank @Pattern(regexp = "\\d{6}") String code) {
    }

    private final UserAccountService users;
    private final TokenService tokens;

    AuthController(UserAccountService users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        UserAccount user = users.authenticate(request.username(), request.password(), http.getRemoteAddr());
        TokenService.IssuedToken token = tokens.issue(user);
        return new LoginResponse(token.token(), "Bearer", token.expiresAt(), user);
    }

    @GetMapping("/me")
    UserAccount me(@AuthenticationPrincipal Jwt jwt) {
        return users.account(jwt.getSubject());
    }

    /** Returns the TOTP secret ONCE, to the authenticated user, while enrollment is not yet confirmed. */
    @PostMapping("/totp/setup")
    UserAccountService.TotpEnrollment setupTotp(@AuthenticationPrincipal Jwt jwt) {
        return users.beginTotpEnrollment(jwt.getSubject());
    }

    @PostMapping("/totp/confirm")
    ResponseEntity<Void> confirmTotp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TotpCode code) {
        users.confirmTotpEnrollment(jwt.getSubject(), code.code());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<Map<String, String>> authenticationFailed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
    }
}
