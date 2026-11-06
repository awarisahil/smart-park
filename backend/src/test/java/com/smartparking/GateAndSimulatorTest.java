package com.smartparking;

import com.smartparking.dto.ReservationRequest;
import com.smartparking.dto.ReservationResponse;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.service.GateService;
import com.smartparking.service.GateStateService;
import com.smartparking.service.ReservationService;
import com.smartparking.service.SensorSimulatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class GateAndSimulatorTest {

    @Autowired
    GateService gateService;

    @Autowired
    GateStateService gateState;

    @Autowired
    ReservationService reservationService;

    @Autowired
    SensorSimulatorService simulator;

    @Autowired
    ReservationRepository reservationRepository;

    @Test
    @Transactional
    void gateEntryChecksInReservedVehicleAndExitBills() {
        ReservationResponse reservation = reservationService.reserve("user@parking.com",
                new ReservationRequest("GATEVH01", null, null, 1));

        ReservationResponse entered = gateService.entry("GATEVH01");
        assertNotNull(entered.entryTime());

        ReservationResponse exited = gateService.exit("GATEVH01");
        assertEquals(ReservationStatus.COMPLETED, exited.status());
        assertTrue(exited.amount() > 0);
    }

    @Test
    @Transactional
    void unknownPlateBecomesWalkInAndBillsOnExit() {
        ReservationResponse entered = gateService.entry("WALKIN99");
        assertNotNull(entered.entryTime());
        assertTrue(entered.synthetic(), "walk-ins are flagged as non-app sessions");

        ReservationResponse exited = gateService.exit("WALKIN99");
        assertTrue(exited.amount() > 0);
    }

    @Test
    @Transactional
    void doubleEntryForSameVehicleRejected() {
        gateService.entry("DUPVH001");
        ApiExceptionLike assertion = assertThrowsRuntime(() -> gateService.entry("DUPVH001"));
        assertTrue(assertion.message.contains("already inside"));
        gateService.exit("DUPVH001");
    }

    @Test
    @Transactional
    void tailgatingRaisesAnAnomaly() {
        int before = gateState.getAnomalies();
        gateService.entry("TAILGT01");
        gateService.entry("TAILGT02");
        assertTrue(gateState.getAnomalies() > before, "two entries within 3s must flag an anomaly");
        gateService.exit("TAILGT01");
        gateService.exit("TAILGT02");
    }

    @Test
    @Transactional
    void simulatorParksBotsAndExpiredBotsLeave() {
        simulator.setEnabled(true);

        int attempts = 0;
        while (simulator.parkedBotCount() == 0 && attempts++ < 6) {
            simulator.runCycle();
        }
        assertTrue(simulator.parkedBotCount() > 0, "simulator should park at least one vehicle");

        // Age every bot beyond its stay so the next cycle sends them out. The same
        // cycle may admit a fresh bot — so assert no *aged* bot survives.
        List<Reservation> aged =
                reservationRepository.findBySyntheticTrueAndStatusAndEntryTimeIsNotNull(ReservationStatus.ACTIVE);
        aged.forEach(bot -> bot.setEntryTime(Instant.now().minus(10, ChronoUnit.MINUTES)));
        simulator.runCycle();

        List<Reservation> remaining =
                reservationRepository.findBySyntheticTrueAndStatusAndEntryTimeIsNotNull(ReservationStatus.ACTIVE);
        boolean anyAged = remaining.stream()
                .anyMatch(bot -> java.time.Duration.between(bot.getEntryTime(), Instant.now()).toMinutes() >= 10);
        assertFalse(anyAged, "expired simulated sessions must check out");
    }

    private interface ThrowingCall {
        void run();
    }

    private record ApiExceptionLike(String message) {
    }

    private ApiExceptionLike assertThrowsRuntime(ThrowingCall call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            return new ApiExceptionLike(e.getMessage());
        }
        fail("expected a RuntimeException");
        return new ApiExceptionLike("");
    }
}
