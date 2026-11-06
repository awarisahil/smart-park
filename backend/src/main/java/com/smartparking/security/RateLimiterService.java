package com.smartparking.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * In-memory sliding-window rate limiter (per key, requests per minute).
 * Sufficient for a single-node deployment; swap for Redis/bucket4j for clusters.
 */
@Service
public class RateLimiterService {

    private final Map<String, Deque<Instant>> windows = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limitPerMinute) {
        Deque<Instant> window = windows.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (window) {
            Instant cutoff = Instant.now().minus(1, ChronoUnit.MINUTES);
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= limitPerMinute) {
                return false;
            }
            window.addLast(Instant.now());
            return true;
        }
    }
}
