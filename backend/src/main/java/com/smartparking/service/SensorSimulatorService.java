package com.smartparking.service;

import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.SlotStatus;
import com.smartparking.model.User;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * IoT sensor-simulation layer: keeps the facility alive with realistic
 * background traffic so the platform can be demonstrated end-to-end without
 * manually driving every vehicle.
 *
 * Ground rules:
 *  - simulated vehicles never starve real users: at least `humanBuffer` slots
 *    are always kept free, and bots are evicted first under pressure;
 *  - bots park up to `targetOccupancy` share of the lot;
 *  - every simulated passage goes through the gate counters and the activity
 *    feed, exactly like a physical barrier would.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorSimulatorService {

    private final ReservationRepository reservationRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final ReservationService reservationService;
    private final GateStateService gateState;
    private final ActivityService activityService;

    @Value("${app.simulator.enabled:true}")
    private boolean configEnabled;

    @Value("${app.simulator.target-occupancy:0.4}")
    private double targetOccupancy;

    @Value("${app.simulator.human-buffer:4}")
    private long humanBuffer;

    @Value("${app.simulator.bot-stay-minutes:3}")
    private long botStayMinutes;

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    @PostConstruct
    void init() {
        enabled.set(configEnabled);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public double getTargetOccupancy() {
        return targetOccupancy;
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
        log.info("Sensor simulator {}", value ? "enabled" : "disabled");
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scheduledCycle() {
        if (enabled.get()) {
            runCycle();
        }
    }

    /** One simulation cycle — also exposed via the admin "run now" endpoint. */
    @Transactional
    public void runCycle() {
        long total = slotRepository.count();
        if (total == 0) {
            return;
        }
        long available = slotRepository.countByStatus(SlotStatus.AVAILABLE);
        List<Reservation> botSessions = parkedBots();

        // 1. Bots whose stay is over leave through the exit gate.
        for (Reservation bot : botSessions) {
            long stayed = Duration.between(bot.getEntryTime(), Instant.now()).toMinutes();
            long stayLimit = botStayMinutes + (bot.getId() % 3);
            if (stayed >= stayLimit) {
                leave(bot, "stay complete");
            }
            available = slotRepository.countByStatus(SlotStatus.AVAILABLE);
        }

        // 2. Pressure: keep room for real users — evict the oldest bot if needed.
        List<Reservation> remaining = parkedBots();
        if (available <= humanBuffer && !remaining.isEmpty()) {
            leave(remaining.get(0), "space needed for incoming traffic");
        }

        // 3. Admission: park a new simulated vehicle while below the target share.
        long botsInside = parkedBots().size();
        double occupancyShare = (double) botsInside / total;
        available = slotRepository.countByStatus(SlotStatus.AVAILABLE);
        if (occupancyShare < targetOccupancy && available > humanBuffer
                && ThreadLocalRandom.current().nextDouble() < 0.7) {
            User walkIn = userRepository.findByEmail("walkin@parking.local").orElse(null);
            if (walkIn != null) {
                String plate = "SIM" + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
                var created = reservationService.createReservation(walkIn, plate, null, null, 2);
                markSynthetic(created.id());
                reservationService.checkIn(created.id());
                gateState.recordEntry();
                activityService.publish("BOT", "Simulated vehicle " + plate + " parked itself");
            }
        }

        // 4. Cross-check barrier counters against real occupancy.
        gateState.checkOccupancyMismatch(slotRepository.countByStatus(SlotStatus.OCCUPIED));
    }

    public long parkedBotCount() {
        return parkedBots().size();
    }

    private List<Reservation> parkedBots() {
        return reservationRepository.findBySyntheticTrueAndStatusAndEntryTimeIsNotNull(ReservationStatus.ACTIVE);
    }

    private void leave(Reservation bot, String reason) {
        reservationService.checkOut(bot.getId());
        gateState.recordExit();
        activityService.publish("BOT", "Simulated vehicle " + bot.getVehicleNumber()
                + " left (" + reason + ")");
    }

    private void markSynthetic(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(r -> r.setSynthetic(true));
    }
}
