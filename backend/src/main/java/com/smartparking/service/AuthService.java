package com.smartparking.service;

import com.smartparking.dto.AuthResponse;
import com.smartparking.dto.LoginRequest;
import com.smartparking.dto.RegisterRequest;
import com.smartparking.exception.ApiException;
import com.smartparking.model.RefreshToken;
import com.smartparking.model.Role;
import com.smartparking.model.User;
import com.smartparking.repository.RefreshTokenRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final RefreshTokenRotation refreshTokenRotation;

    @Value("${app.refresh-token.expiry-days}")
    private long refreshExpiryDays;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        User user = new User(
                request.name().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.USER
        );
        userRepository.save(user);
        auditService.record("REGISTER", email, "new account created");
        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        if (loginAttemptService.isLocked(email)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Try again in "
                            + loginAttemptService.remainingLockMinutes(email) + " minutes.");
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (BadCredentialsException e) {
            loginAttemptService.onFailure(email);
            auditService.record("LOGIN_FAILED", email,
                    loginAttemptService.isLocked(email) ? "account locked (5 failures)" : "bad credentials");
            if (loginAttemptService.isLocked(email)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed attempts. Account locked for 10 minutes.");
            }
            throw e;
        }
        loginAttemptService.onSuccess(email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        auditService.record("LOGIN", email, "login successful");
        return issueTokens(user);
    }

    /**
     * Rotates the refresh token: the presented token is revoked and a fresh
     * pair is issued. A presented token that was already revoked indicates
     * theft — the whole family is revoked.
     */
    @Transactional
    public AuthResponse refresh(String presentedToken) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(presentedToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (stored.isRevoked()) {
            refreshTokenRotation.revokeFamilyOf(stored.getUser());
            auditService.record("REFRESH_REUSE_DETECTED", stored.getUser().getEmail(),
                    "revoked refresh token presented — family revoked");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected. Please sign in again.");
        }
        if (stored.isExpired()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired. Please sign in again.");
        }
        stored.setRevoked(true);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(String presentedToken) {
        refreshTokenRotation.revokeOne(sha256(presentedToken));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole().name(), accessToken, issueRefreshToken(user));
    }

    private String issueRefreshToken(User user) {
        String raw = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        refreshTokenRepository.save(new RefreshToken(
                user,
                sha256(raw),
                Instant.now().plus(refreshExpiryDays, ChronoUnit.DAYS)));
        return raw;
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
