package com.smartparking.service;

import com.smartparking.config.DomainEvents;
import com.smartparking.event.SlotFreedEvent;
import com.smartparking.model.NotificationType;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.SlotStatus;
import com.smartparking.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Background sweeper that:
 *  - warns owners ~5 minutes before their entry window closes,
 *  - cancels reservations whose entry window has expired, releasing the slot,
 *  - publishes SlotFreedEvent so the waitlist engine can auto-allocate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationExpiryService {

    private static final long WARNING_LEAD_MINUTES = 5;

    private final ReservationRepository reservationRepository;
    private final SlotService slotService;
    private final NotificationService notificationService;
    private final DomainEvents domainEvents;
    private final ActivityService activityService;

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    @Transactional
    public void sweepExpiringReservations() {
        warnExpiringSoon();
        expireUnclaimed();
    }

    private void warnExpiringSoon() {
        List<Reservation> expiringSoon = reservationRepository
                .findByStatusAndEntryTimeIsNullAndWarningSentFalseAndExpiresAtBefore(
                        ReservationStatus.ACTIVE, Instant.now().plus(WARNING_LEAD_MINUTES, ChronoUnit.MINUTES));
        for (Reservation reservation : expiringSoon) {
            reservation.setWarningSent(true);
            notificationService.notifyUser(reservation.getUser(), NotificationType.WARNING,
                    "Entry window for slot " + reservation.getSlot().getSlotNumber()
                            + " closes in about " + WARNING_LEAD_MINUTES + " minutes — head to the gate");
        }
    }

    private void expireUnclaimed() {
        List<Reservation> expired = reservationRepository
                .findByStatusAndEntryTimeIsNullAndExpiresAtBefore(ReservationStatus.ACTIVE, Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        for (Reservation reservation : expired) {
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.getSlot().setStatus(SlotStatus.AVAILABLE);
            notificationService.notifyUser(reservation.getUser(), NotificationType.WARNING,
                    "Reservation for slot " + reservation.getSlot().getSlotNumber()
                            + " expired — the entry window was missed");
            activityService.publish("EXPIRED", reservation.getSlot(),
                    "Hold expired — slot " + reservation.getSlot().getSlotNumber() + " released");
            log.info("Reservation #{} expired (code {}), slot {} released",
                    reservation.getId(), reservation.getEntryCode(),
                    reservation.getSlot().getSlotNumber());
        }
        slotService.broadcastBoard();
        domainEvents.publishAfterCommit(new SlotFreedEvent());
    }
}
