package com.smartparking;

import com.smartparking.dto.*;
import com.smartparking.model.*;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class ReservationFlowTest {

    @Autowired
    ReservationService reservationService;

    @Autowired
    WaitlistService waitlistService;

    @Autowired
    BookingService bookingService;

    @Autowired
    PassService passService;

    @Autowired
    ParkingSlotRepository slotRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void reserveThenCancelFreesTheSlot() {
        long availableBefore = slotRepository.countByStatus(SlotStatus.AVAILABLE);

        ReservationResponse reservation =
                reservationService.reserve("user@parking.com",
                        new ReservationRequest("FLOWTEST1", null, null, 2));

        assertEquals(ReservationStatus.ACTIVE, reservation.status());
        assertNotNull(reservation.entryCode());
        assertEquals(6, reservation.entryCode().length());
        assertNotNull(reservation.expiresAt());
        assertEquals(availableBefore - 1, slotRepository.countByStatus(SlotStatus.AVAILABLE));

        ReservationResponse cancelled =
                reservationService.cancel("user@parking.com", reservation.id());
        assertEquals(ReservationStatus.CANCELLED, cancelled.status());
        assertEquals(availableBefore, slotRepository.countByStatus(SlotStatus.AVAILABLE));
    }

    @Test
    @Transactional
    void billingUsesCommittedTimeIncludingExtensions() {
        ReservationResponse reservation =
                reservationService.reserve("user@parking.com",
                        new ReservationRequest("FLOWTEST2", null, null, 1));
        reservationService.checkIn(reservation.id());
        ReservationResponse extended =
                reservationService.extend("user@parking.com", reservation.id(),
                        new ExtensionRequest(2));
        assertEquals(2, extended.extensionHours());

        ReservationResponse done = reservationService.checkOut(reservation.id());
        assertEquals(ReservationStatus.COMPLETED, done.status());
        assertNotNull(done.exitTime());

        double rate = slotRepository.findBySlotNumber(done.slotNumber()).orElseThrow().getHourlyRate();
        // 1 expected + 2 extension hours committed → 3 billable hours.
        assertEquals(rate * 3, done.amount(), 0.001);
    }

    @Test
    @Transactional
    void slotTypePreferenceIsHonored() {
        ReservationResponse ev = reservationService.reserve("user@parking.com",
                new ReservationRequest("FLOWTEST3", null, SlotType.EV_CHARGING, 1));
        ParkingSlot slot = slotRepository.findBySlotNumber(ev.slotNumber()).orElseThrow();
        assertEquals(SlotType.EV_CHARGING, slot.getType());
    }

    @Test
    @Transactional
    void waitlistEntryIsFulfilledWhenSlotFrees() {
        User queuedUser = userRepository.save(
                new User("WL User", "wl@test.com", passwordEncoder.encode("wl12345"), Role.USER));
        User driver = userRepository.save(
                new User("Driver", "wldrv@test.com", passwordEncoder.encode("wl12345"), Role.USER));

        // Fill the queue first.
        WaitlistResponse queued = waitlistService.join(queuedUser.getEmail(),
                new WaitlistRequest("WLVEH001", null, null));
        assertEquals(1, queued.position());

        // A slot frees up → engine allocates it to the queued driver.
        ReservationResponse parked = reservationService.reserve(driver.getEmail(),
                new ReservationRequest("WLVEH002", null, null, 1));
        reservationService.checkIn(parked.id());
        reservationService.checkOut(parked.id());

        waitlistService.scheduledFulfillment();

        assertNull(waitlistService.myPosition(queuedUser.getEmail()), "queue entry should be consumed");
        boolean allocated = reservationRepository.findByUserOrderByReservedAtDesc(queuedUser).stream()
                .anyMatch(r -> r.getStatus() == ReservationStatus.ACTIVE
                        && r.getVehicleNumber().equals("WLVEH001"));
        assertTrue(allocated, "queued vehicle should hold an active reservation");
    }

    @Test
    @Transactional
    void seasonPassCoversCheckoutAtZeroRupees() {
        PassResponse pass = passService.purchase("user@parking.com", new PassRequest("A", 1));
        assertTrue(pass.validNow());

        ReservationResponse reservation = reservationService.reserve("user@parking.com",
                new ReservationRequest("FLOWTEST4", "A", null, 1));
        reservationService.checkIn(reservation.id());
        ReservationResponse done = reservationService.checkOut(reservation.id());

        assertEquals(0.0, done.amount(), 0.001);
        assertTrue(done.passCovered());
    }

    @Test
    @Transactional
    void scheduledBookingIsAllocatedBeforeStart() {
        Instant start = Instant.now().plus(2, ChronoUnit.MINUTES);
        bookingService.create("user@parking.com",
                new BookingRequest("FLOWTEST5", null, null, start, 2));

        bookingService.allocateDueBookings();

        List<BookingResponse> bookings = bookingService.myBookings("user@parking.com");
        BookingResponse allocated = bookings.stream()
                .filter(b -> b.vehicleNumber().equals("FLOWTEST5"))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.ALLOCATED, allocated.status());
        assertNotNull(allocated.slotNumber());
        assertNotNull(allocated.reservationId());
    }
}
