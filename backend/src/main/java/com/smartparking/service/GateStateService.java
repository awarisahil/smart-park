package com.smartparking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory state of the physical entry/exit barriers: passage counters,
 * tailgating detection and gate-vs-slot occupancy cross-checks.
 */
@Service
@RequiredArgsConstructor
public class GateStateService {

    private static final long TAILGATE_WINDOW_SECONDS = 3;

    private final ActivityService activityService;

    private final AtomicLong entries = new AtomicLong();
    private final AtomicLong exits = new AtomicLong();
    private final AtomicInteger anomalies = new AtomicInteger();
    private final AtomicReference<Instant> lastEntryAt = new AtomicReference<>();

    public void recordEntry() {
        Instant now = Instant.now();
        Instant previous = lastEntryAt.getAndSet(now);
        entries.incrementAndGet();
        if (previous != null
                && Duration.between(previous, now).getSeconds() < TAILGATE_WINDOW_SECONDS) {
            anomalies.incrementAndGet();
            activityService.publish("ANOMALY",
                    "Possible tailgating at the entry gate — two vehicles within "
                            + TAILGATE_WINDOW_SECONDS + "s");
        }
    }

    public void recordExit() {
        exits.incrementAndGet();
    }

    /** Cross-check the barrier counters against the actual occupied slots. */
    public void checkOccupancyMismatch(long actualOccupied) {
        long insideByGates = entries.get() - exits.get();
        if (insideByGates != actualOccupied) {
            anomalies.incrementAndGet();
            activityService.publish("ANOMALY",
                    "Occupancy mismatch — barriers count " + insideByGates
                            + " vehicles inside but " + actualOccupied + " slots are occupied");
        }
    }

    public long getEntries() {
        return entries.get();
    }

    public long getExits() {
        return exits.get();
    }

    public long getInsideByGates() {
        return entries.get() - exits.get();
    }

    public int getAnomalies() {
        return anomalies.get();
    }
}
