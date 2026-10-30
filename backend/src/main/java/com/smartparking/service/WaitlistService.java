package com.smartparking.service;

import com.smartparking.dto.WaitlistRequest;
import com.smartparking.dto.WaitlistResponse;
import com.smartparking.event.SlotFreedEvent;
import com.smartparking.exception.ApiException;
import com.smartparking.model.*;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.repository.WaitlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Smart waitlist: when the lot is full, users queue up with optional zone /
 * slot-type preferences. Whenever a slot frees up (cancel, check-out, expiry)
 * the first queued entry that matches gets a reservation automatically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistEntryRepository waitlistRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final ReservationService reservationService;
    private final NotificationService notificationService;
    private final ActivityService activityService;

    @Transactional
    public WaitlistResponse join(String email, WaitlistRequest request) {
        User user = findUser(email);
        if (reservationRepository.existsByUserAndStatus(user, ReservationStatus.ACTIVE)) {
            throw ApiException.conflict("You have an active reservation — no need to wait");
        }
        if (waitlistRepository.existsByUserAndStatus(user, WaitlistStatus.QUEUED)) {
            throw ApiException.conflict("You are already on the waitlist");
        }
        WaitlistEntry entry = new WaitlistEntry(
                user,
                request.vehicleNumber().trim().toUpperCase(),
                request.zone() == null || request.zone().isBlank() ? null : request.zone().trim().toUpperCase(),
                request.preferredType());
        waitlistRepository.save(entry);
        notificationService.notifyUser(user, NotificationType.INFO,
                "You joined the parking waitlist — we'll allocate a slot automatically when one frees up");
        return toResponse(entry);
    }

    @Transactional(readOnly = true)
    public WaitlistResponse myPosition(String email) {
        User user = findUser(email);
        return waitlistRepository.findByStatusOrderByCreatedAtAsc(WaitlistStatus.QUEUED).stream()
                .filter(entry -> entry.getUser().getId().equals(user.getId()))
                .findFirst()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public void leave(String email, Long id) {
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Waitlist entry not found"));
        if (!entry.getUser().getEmail().equalsIgnoreCase(email)) {
            throw ApiException.forbidden("You can only leave your own queue entry");
        }
        if (entry.getStatus() != WaitlistStatus.QUEUED) {
            throw ApiException.conflict("This waitlist entry is no longer queued");
        }
        entry.setStatus(WaitlistStatus.CANCELLED);
    }

    /** Runs after any transaction that freed a slot. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSlotFreed(SlotFreedEvent event) {
        fulfillQueued();
    }

    /** Safety net: periodic sweep in case a free slot was missed. */
    @Scheduled(fixedRate = 30_000, initialDelay = 45_000)
    @Transactional
    public void scheduledFulfillment() {
        fulfillQueued();
    }

    private void fulfillQueued() {
        List<WaitlistEntry> queue = waitlistRepository.findByStatusOrderByCreatedAtAsc(WaitlistStatus.QUEUED);
        for (WaitlistEntry entry : queue) {
            tryFulfill(entry);
        }
    }

    private boolean tryFulfill(WaitlistEntry entry) {
        List<ParkingSlot> candidates = slotRepository
                .lockSlotsByStatusAndZoneAndType(SlotStatus.AVAILABLE, entry.getZone(), entry.getSlotType());
        if (candidates.isEmpty()) {
            return false;
        }
        if (reservationRepository.existsByUserAndStatus(entry.getUser(), ReservationStatus.ACTIVE)) {
            entry.setStatus(WaitlistStatus.CANCELLED);
            return true;
        }
        var response = reservationService.createReservation(
                entry.getUser(), entry.getVehicleNumber(), entry.getZone(), entry.getSlotType(), 2);
        entry.setStatus(WaitlistStatus.FULFILLED);
        entry.setFulfilledAt(Instant.now());
        activityService.publish("WAITLIST", slotRepository.findById(response.id()).orElseThrow(),
                "Waitlist auto-allocation at " + response.slotNumber());
        log.info("Waitlist entry #{} fulfilled with slot {} (code {})",
                entry.getId(), response.slotNumber(), response.entryCode());
        return true;
    }

    private WaitlistResponse toResponse(WaitlistEntry entry) {
        List<WaitlistEntry> queue = waitlistRepository.findByStatusOrderByCreatedAtAsc(WaitlistStatus.QUEUED);
        long position = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId().equals(entry.getId())) {
                position = i + 1;
                break;
            }
        }
        return new WaitlistResponse(
                entry.getId(),
                entry.getVehicleNumber(),
                entry.getZone(),
                entry.getSlotType(),
                entry.getStatus(),
                position,
                queue.size(),
                entry.getCreatedAt()
        );
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
