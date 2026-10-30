package com.smartparking.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory login brute-force protection: after 5 failed attempts an account
 * is locked out for 10 minutes. Resets on successful login.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 10;

    private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        Instant until = lockedUntil.get(normalize(email));
        return until != null && until.isAfter(Instant.now());
    }

    public long remainingLockMinutes(String email) {
        Instant until = lockedUntil.get(normalize(email));
        if (until == null) {
            return 0;
        }
        return Math.max(1, Duration.between(Instant.now(), until).toMinutes());
    }

    public void onFailure(String email) {
        String key = normalize(email);
        int count = attempts.merge(key, 1, Integer::sum);
        if (count >= MAX_ATTEMPTS) {
            lockedUntil.put(key, Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
            attempts.remove(key);
        }
    }

    public void onSuccess(String email) {
        String key = normalize(email);
        attempts.remove(key);
        lockedUntil.remove(key);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
