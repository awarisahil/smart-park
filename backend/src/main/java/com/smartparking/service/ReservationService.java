package com.smartparking.service;

import com.smartparking.dto.*;
import com.smartparking.event.SlotFreedEvent;
import com.smartparking.exception.ApiException;
import com.smartparking.model.*;
import com.smartparking.config.DomainEvents;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final SlotService slotService;
    private final NotificationService notificationService;
    private final DomainEvents domainEvents;
    private final ActivityService activityService;
    private final PassService passService;

    @Value("${app.reservation.hold-minutes}")
    private long holdMinutes;

    /**
     * Automated slot allocation: validates the request, then creates the
     * reservation under a database row lock so concurrent requests can never
     * double-book the same slot. Supports zone and slot-type preferences.
     */
    @Transactional
    public ReservationResponse reserve(String email, ReservationRequest request) {
        User user = findUser(email);

        if (reservationRepository.existsByUserAndStatus(user, ReservationStatus.ACTIVE)) {
            throw ApiException.conflict("You already have an active reservation. Cancel it before booking a new one.");
        }
        String vehicleNumber = normalizeVehicle(request.vehicleNumber());
        if (reservationRepository.existsByVehicleNumberAndStatus(vehicleNumber, ReservationStatus.ACTIVE)) {
            throw ApiException.conflict("Vehicle " + vehicleNumber + " already has an active reservation");
        }

        String zone = normalizeZone(request.zone());
        int expectedHours = request.expectedHours() != null ? request.expectedHours() : 2;
        ensureSlotAvailable(zone, request.preferredType());
        return createReservation(user, vehicleNumber, zone, request.preferredType(), expectedHours);
    }

    /** Shared creation logic used by direct reservations and waitlist fulfillment. */
    @Transactional
    public ReservationResponse createReservation(User user, String vehicleNumber, String zone,
                                                 SlotType type, int expectedHours) {
        List<ParkingSlot> candidates =
                slotRepository.lockSlotsByStatusAndZoneAndType(SlotStatus.AVAILABLE, zone, type);
        if (candidates.isEmpty()) {
            throw ApiException.conflict("No slots available for your selection");
        }
        ParkingSlot slot = candidates.get(0);
        slot.setStatus(SlotStatus.RESERVED);
        Reservation reservation = new Reservation(user, slot, vehicleNumber, generateEntryCode());
        reservation.setExpiresAt(Instant.now().plus(holdMinutes, ChronoUnit.MINUTES));
        reservation.setExpectedHours(expectedHours);
        reservationRepository.save(reservation);
        notificationService.notifyUser(user, NotificationType.SUCCESS,
                "Slot " + slot.getSlotNumber() + " reserved for " + vehicleNumber
                        + " — entry code " + reservation.getEntryCode()
                        + " (valid for " + holdMinutes + " min)");
        activityService.publish("RESERVED", slot, "Slot " + slot.getSlotNumber() + " reserved via app");
        slotService.broadcastBoard();
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancel(String email, Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        User user = findUser(email);
        boolean isOwner = reservation.getUser().getId().equals(user.getId());
        if (!isOwner && user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("You can only cancel your own reservation");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw ApiException.conflict("Reservation is already " + reservation.getStatus().name().toLowerCase());
        }
        if (reservation.getSlot().getStatus() == SlotStatus.OCCUPIED) {
            throw ApiException.conflict("Vehicle is parked. Ask an attendant to check it out first.");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.getSlot().setStatus(SlotStatus.AVAILABLE);
        notificationService.notifyUser(reservation.getUser(), NotificationType.INFO,
                "Reservation for slot " + reservation.getSlot().getSlotNumber() + " was cancelled");
        activityService.publish("CANCELLED", reservation.getSlot(),
                "Reservation for slot " + reservation.getSlot().getSlotNumber() + " cancelled");
        slotService.broadcastBoard();
        domainEvents.publishAfterCommit(new SlotFreedEvent());
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse checkIn(Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw ApiException.conflict("Reservation is " + reservation.getStatus().name().toLowerCase());
        }
        if (reservation.getSlot().getStatus() != SlotStatus.RESERVED) {
            throw ApiException.conflict("Slot " + reservation.getSlot().getSlotNumber()
                    + " is not reserved for this vehicle");
        }
        reservation.getSlot().setStatus(SlotStatus.OCCUPIED);
        reservation.setEntryTime(Instant.now());
        notificationService.notifyUser(reservation.getUser(), NotificationType.INFO,
                "Vehicle " + reservation.getVehicleNumber() + " checked in at slot "
                        + reservation.getSlot().getSlotNumber());
        activityService.publish("CHECK_IN", reservation.getSlot(),
                "Vehicle checked in at " + reservation.getSlot().getSlotNumber());
        slotService.broadcastBoard();
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse checkOut(Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw ApiException.conflict("Reservation is " + reservation.getStatus().name().toLowerCase());
        }
        if (reservation.getSlot().getStatus() != SlotStatus.OCCUPIED) {
            throw ApiException.conflict("Vehicle has not checked in yet");
        }
        reservation.getSlot().setStatus(SlotStatus.AVAILABLE);
        reservation.setExitTime(Instant.now());
        boolean passCovered = passService.hasValidPass(reservation.getUser(), reservation.getSlot().getZone());
        reservation.setPassCovered(passCovered);
        reservation.setAmount(passCovered ? 0.0 : billableAmount(reservation));
        reservation.setStatus(ReservationStatus.COMPLETED);
        notificationService.notifyUser(reservation.getUser(), NotificationType.SUCCESS,
                passCovered
                        ? "Session complete — slot " + reservation.getSlot().getSlotNumber()
                        + " covered by your season pass (₹0). See you next time!"
                        : "Session complete — slot " + reservation.getSlot().getSlotNumber()
                        + ", billed ₹" + String.format("%.2f", reservation.getAmount())
                        + ". Thank you for parking with us!");
        activityService.publish("CHECK_OUT", reservation.getSlot(),
                "Vehicle left " + reservation.getSlot().getSlotNumber() + " — slot free");
        slotService.broadcastBoard();
        domainEvents.publishAfterCommit(new SlotFreedEvent());
        return toResponse(reservation);
    }

    /** Extend a parked session; committed time (expected + extensions) is billed. */
    @Transactional
    public ReservationResponse extend(String email, Long reservationId, ExtensionRequest request) {
        Reservation reservation = findReservation(reservationId);
        User user = findUser(email);
        boolean isOwner = reservation.getUser().getId().equals(user.getId());
        if (!isOwner && user.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("You can only extend your own reservation");
        }
        if (reservation.getStatus() != ReservationStatus.ACTIVE || reservation.getEntryTime() == null) {
            throw ApiException.conflict("Only parked sessions can be extended");
        }
        reservation.setExtensionHours(reservation.getExtensionHours() + request.additionalHours());
        notificationService.notifyUser(reservation.getUser(), NotificationType.INFO,
                "Session extended by " + request.additionalHours() + "h — slot "
                        + reservation.getSlot().getSlotNumber());
        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> myReservations(String email) {
        return reservationRepository.findByUserOrderByReservedAtDesc(findUser(email))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MyStatsResponse myStats(String email) {
        List<Reservation> mine = reservationRepository.findByUserOrderByReservedAtDesc(findUser(email));
        long completed = mine.stream().filter(r -> r.getStatus() == ReservationStatus.COMPLETED).count();
        boolean active = mine.stream().anyMatch(r -> r.getStatus() == ReservationStatus.ACTIVE);
        double spent = mine.stream()
                .filter(r -> r.getStatus() == ReservationStatus.COMPLETED && r.getAmount() != null)
                .mapToDouble(Reservation::getAmount)
                .sum();
        return new MyStatsResponse(completed, Math.round(spent * 100.0) / 100.0, active);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public List<ReservationResponse> allReservations() {
        return reservationRepository.findByStatusOrderByReservedAtDesc(ReservationStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    /** Paginated, filterable session log for admins. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ReservationResponse> sessions(String status, String zone,
                                              LocalDate from, LocalDate to,
                                              int page, int size) {
        Specification<Reservation> spec = buildSpec(status, zone, from, to);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reservedAt"));
        return reservationRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /** CSV of the caller's own parking history. */
    @Transactional(readOnly = true)
    public byte[] myExportCsv(String email) {
        List<Reservation> mine = reservationRepository.findByUserOrderByReservedAtDesc(findUser(email));
        return buildCsv(mine);
    }

    /** CSV of every reservation — admin only. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public byte[] exportCsv() {
        return buildCsv(reservationRepository.findAll(Sort.by(Sort.Direction.DESC, "reservedAt")));
    }

    private Specification<Reservation> buildSpec(String status, String zone,
                                                 LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), ReservationStatus.valueOf(status.trim().toUpperCase())));
            }
            if (zone != null && !zone.isBlank()) {
                predicates.add(cb.equal(root.get("slot").get("zone"), zone.trim().toUpperCase()));
            }
            ZoneId zoneId = ZoneId.systemDefault();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("reservedAt"),
                        from.atStartOfDay(zoneId).toInstant()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("reservedAt"),
                        to.plusDays(1).atStartOfDay(zoneId).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private byte[] buildCsv(List<Reservation> reservations) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,user,email,vehicle,slot,zone,status,reserved_at,entry_time,exit_time,expected_hours,extension_hours,amount,pass_covered,synthetic\n");
        for (Reservation r : reservations) {
            sb.append(r.getId()).append(',')
                    .append(csv(r.getUser().getName())).append(',')
                    .append(csv(r.getUser().getEmail())).append(',')
                    .append(csv(r.getVehicleNumber())).append(',')
                    .append(csv(r.getSlot().getSlotNumber())).append(',')
                    .append(csv(r.getSlot().getZone())).append(',')
                    .append(r.getStatus()).append(',')
                    .append(r.getReservedAt()).append(',')
                    .append(r.getEntryTime()).append(',')
                    .append(r.getExitTime()).append(',')
                    .append(r.getExpectedHours()).append(',')
                    .append(r.getExtensionHours()).append(',')
                    .append(r.getAmount() == null ? "" : r.getAmount()).append(',')
                    .append(r.isPassCovered()).append(',')
                    .append(r.isSynthetic()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * Billing: the greater of actual parked time and the committed duration
     * (expected + extension hours), rounded up to whole hours (min 1 hour).
     */
    private double billableAmount(Reservation reservation) {
        long actualMinutes = Math.max(1,
                Duration.between(reservation.getEntryTime(), reservation.getExitTime()).toMinutes());
        long committedMinutes = (reservation.getExpectedHours() + reservation.getExtensionHours()) * 60L;
        long billableMinutes = Math.max(actualMinutes, committedMinutes);
        double hours = Math.ceil(billableMinutes / 60.0);
        return Math.round(reservation.getSlot().getHourlyRate() * hours * 100.0) / 100.0;
    }

    private void ensureSlotAvailable(String zone, SlotType type) {
        List<ParkingSlot> candidates =
                slotRepository.lockSlotsByStatusAndZoneAndType(SlotStatus.AVAILABLE, zone, type);
        if (candidates.isEmpty()) {
            String selection = zone != null ? "zone " + zone : "your selection";
            if (type != null) {
                selection += " (" + type.name().toLowerCase().replace('_', ' ') + ")";
            }
            throw ApiException.conflict("No slots available in " + selection);
        }
    }

    private String normalizeVehicle(String vehicleNumber) {
        return vehicleNumber.trim().toUpperCase();
    }

    private String normalizeZone(String zone) {
        return (zone == null || zone.isBlank()) ? null : zone.trim().toUpperCase();
    }

    private String generateEntryCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private Reservation findReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Reservation not found"));
    }

    private ReservationResponse toResponse(Reservation reservation) {
        boolean overstay = reservation.getStatus() == ReservationStatus.ACTIVE
                && reservation.getEntryTime() != null
                && Instant.now().isAfter(reservation.getEntryTime()
                        .plus(reservation.getExpectedHours() + reservation.getExtensionHours(), ChronoUnit.HOURS));
        return new ReservationResponse(
                reservation.getId(),
                reservation.getSlot().getSlotNumber(),
                reservation.getSlot().getZone(),
                reservation.getVehicleNumber(),
                reservation.getUser().getName(),
                reservation.getEntryCode(),
                reservation.getStatus(),
                reservation.getReservedAt(),
                reservation.getExpiresAt(),
                reservation.getEntryTime(),
                reservation.getExitTime(),
                reservation.getAmount(),
                reservation.getExpectedHours(),
                reservation.getExtensionHours(),
                overstay,
                reservation.isSynthetic(),
                reservation.isPassCovered()
        );
    }
}
