package com.smartparking.service;

import com.smartparking.dto.BookingRequest;
import com.smartparking.dto.BookingResponse;
import com.smartparking.dto.ReservationResponse;
import com.smartparking.exception.ApiException;
import com.smartparking.model.*;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ScheduledBookingRepository;
import com.smartparking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Advance bookings: the driver schedules a window, and shortly before it
 * starts the allocation engine assigns the best currently-available matching
 * slot — no slot is held all day. If the lot is full at start time the engine
 * retries during a 15-minute grace window, then the booking expires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final long ALLOCATION_LEAD_MINUTES = 15;
    private static final long GRACE_MINUTES = 15;
    private static final int MAX_UPCOMING_PER_USER = 3;

    private final ScheduledBookingRepository bookingRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final ReservationService reservationService;
    private final NotificationService notificationService;

    @Transactional
    public BookingResponse create(String email, BookingRequest request) {
        User user = findUser(email);
        Instant startTime = request.startTime();
        Instant now = Instant.now();
        if (!startTime.isAfter(now)) {
            throw ApiException.badRequest("Start time must be in the future");
        }
        if (startTime.isAfter(now.plus(7, ChronoUnit.DAYS))) {
            throw ApiException.badRequest("Bookings can be scheduled up to 7 days ahead");
        }
        if (bookingRepository.countByUserAndStatus(user, BookingStatus.UPCOMING) >= MAX_UPCOMING_PER_USER) {
            throw ApiException.conflict("You can have up to " + MAX_UPCOMING_PER_USER + " upcoming bookings");
        }
        String vehicle = request.vehicleNumber().trim().toUpperCase();
        if (bookingRepository.existsByUserAndVehicleNumberAndStatus(user, vehicle, BookingStatus.UPCOMING)) {
            throw ApiException.conflict("Vehicle " + vehicle + " already has an upcoming booking");
        }
        String zone = request.zone() == null || request.zone().isBlank()
                ? null : request.zone().trim().toUpperCase();
        ScheduledBooking booking = new ScheduledBooking(
                user, vehicle, zone, request.slotType(), startTime,
                request.expectedHours() != null ? request.expectedHours() : 2);
        bookingRepository.save(booking);
        notificationService.notifyUser(user, NotificationType.INFO,
                "Booking confirmed for " + vehicle + " at " + startTime
                        + " — we'll allocate your slot automatically before you arrive");
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> myBookings(String email) {
        return bookingRepository.findByUserOrderByStartTimeAsc(findUser(email))
                .stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .map(this::toResponse).toList();
    }

    @Transactional
    public void cancel(String email, Long id) {
        ScheduledBooking booking = bookingRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!booking.getUser().getEmail().equalsIgnoreCase(email)) {
            throw ApiException.forbidden("You can only cancel your own booking");
        }
        if (booking.getStatus() != BookingStatus.UPCOMING) {
            throw ApiException.conflict("Only upcoming bookings can be cancelled here");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        notificationService.notifyUser(booking.getUser(), NotificationType.INFO,
                "Booking for " + booking.getVehicleNumber() + " cancelled");
    }

    /** Runs frequently so allocations land promptly as windows approach. */
    @Scheduled(fixedRate = 30_000, initialDelay = 20_000)
    @Transactional
    public void allocateDueBookings() {
        Instant horizon = Instant.now().plus(ALLOCATION_LEAD_MINUTES, ChronoUnit.MINUTES);
        List<ScheduledBooking> due =
                bookingRepository.findByStatusAndStartTimeBeforeOrderByStartTimeAsc(BookingStatus.UPCOMING, horizon);
        for (ScheduledBooking booking : due) {
            allocate(booking);
        }
    }

    private void allocate(ScheduledBooking booking) {
        String zone = booking.getZone();
        var candidates = slotRepository.lockSlotsByStatusAndZoneAndType(
                SlotStatus.AVAILABLE, zone, booking.getSlotType());
        if (candidates.isEmpty()) {
            long pastGrace = Duration.between(booking.getStartTime(), Instant.now()).toMinutes();
            if (pastGrace > GRACE_MINUTES) {
                booking.setStatus(BookingStatus.EXPIRED);
                notificationService.notifyUser(booking.getUser(), NotificationType.WARNING,
                        "We couldn't find a slot for your " + booking.getStartTime()
                                + " booking — the facility was full. No charge was made");
                log.info("Scheduled booking #{} expired unallocated (lot full)", booking.getId());
            }
            return;
        }
        ReservationResponse reservation = reservationService.createReservation(
                booking.getUser(), booking.getVehicleNumber(), zone, booking.getSlotType(),
                booking.getExpectedHours());
        booking.setReservationId(reservation.id());
        booking.setSlotNumber(reservation.slotNumber());
        booking.setStatus(BookingStatus.ALLOCATED);
        notificationService.notifyUser(booking.getUser(), NotificationType.SUCCESS,
                "Your slot is ready: " + reservation.slotNumber() + " — entry code "
                        + reservation.entryCode() + ". Enter within the hold window");
        log.info("Scheduled booking #{} allocated to slot {}",
                booking.getId(), reservation.slotNumber());
    }

    private BookingResponse toResponse(ScheduledBooking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getVehicleNumber(),
                booking.getZone(),
                booking.getSlotType(),
                booking.getStartTime(),
                booking.getExpectedHours(),
                booking.getStatus(),
                booking.getSlotNumber(),
                booking.getReservationId());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
