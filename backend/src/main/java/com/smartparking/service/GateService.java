package com.smartparking.service;

import com.smartparking.dto.ReservationResponse;
import com.smartparking.exception.ApiException;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.User;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gate controller API — the endpoints a barrier/ANPR camera would call.
 * Entry matches the plate to a reservation (or creates a walk-in session when
 * the lot has space); exit settles the bill and opens the barrier.
 */
@Service
@RequiredArgsConstructor
public class GateService {

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ReservationService reservationService;
    private final GateStateService gateState;
    private final ActivityService activityService;

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ReservationResponse entry(String plate) {
        String vehicle = plate.trim().toUpperCase();
        var existing = reservationRepository
                .findFirstByVehicleNumberAndStatusOrderByReservedAtDesc(vehicle, ReservationStatus.ACTIVE);
        if (existing.isPresent()) {
            Reservation reservation = existing.get();
            if (reservation.getEntryTime() != null) {
                throw ApiException.conflict("Vehicle " + vehicle + " is already inside the facility");
            }
            ReservationResponse response = reservationService.checkIn(reservation.getId());
            gateState.recordEntry();
            activityService.publish("GATE", "Entry gate opened for " + vehicle + " → " + response.slotNumber());
            return response;
        }

        // Walk-in: no reservation — allocate on the spot if space allows.
        User walkIn = userRepository.findByEmail("walkin@parking.local")
                .orElseThrow(() -> ApiException.conflict("Walk-in guest account is missing"));
        ReservationResponse created = reservationService.createReservation(walkIn, vehicle, null, null, 2);
        markSynthetic(created.id());
        ReservationResponse response = reservationService.checkIn(created.id());
        gateState.recordEntry();
        activityService.publish("GATE", "Walk-in " + vehicle + " allocated " + response.slotNumber() + " at the gate");
        return response;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ReservationResponse exit(String plate) {
        String vehicle = plate.trim().toUpperCase();
        var existing = reservationRepository
                .findFirstByVehicleNumberAndStatusOrderByReservedAtDesc(vehicle, ReservationStatus.ACTIVE);
        if (existing.isEmpty() || existing.get().getEntryTime() == null) {
            throw ApiException.notFound("Vehicle " + vehicle + " is not parked inside");
        }
        ReservationResponse response = reservationService.checkOut(existing.get().getId());
        gateState.recordExit();
        activityService.publish("GATE", "Exit gate opened for " + vehicle
                + (response.amount() != null && response.amount() == 0
                ? " (covered by pass)" : " — settled " + response.amount()));
        return response;
    }

    private void markSynthetic(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(r -> r.setSynthetic(true));
    }
}
