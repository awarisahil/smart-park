package com.smartparking.service;

import com.smartparking.dto.*;
import com.smartparking.exception.ApiException;
import com.smartparking.model.*;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.websocket.SlotNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SlotService {

    /** Default hourly rates per zone, used when a slot is created without an explicit rate. */
    private static final Map<String, Double> DEFAULT_RATES = Map.of(
            "A", 60.0,
            "B", 50.0,
            "C", 40.0,
            "D", 35.0,
            "E", 30.0,
            "F", 25.0);

    private final ParkingSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final SlotNotifier notifier;
    private final ActivityService activityService;

    @Transactional(readOnly = true)
    public SlotBoardResponse getBoard() {
        return toBoard(slotRepository.findAll(slotSort()));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SlotResponse createSlot(SlotRequest request) {
        String slotNumber = request.slotNumber().trim().toUpperCase();
        if (slotRepository.existsBySlotNumber(slotNumber)) {
            throw ApiException.conflict("Slot " + slotNumber + " already exists");
        }
        String zone = request.zone().trim().toUpperCase();
        double rate = request.hourlyRate() != null
                ? request.hourlyRate()
                : DEFAULT_RATES.getOrDefault(zone, 30.0);
        ParkingSlot slot = new ParkingSlot(slotNumber, zone, rate);
        if (request.type() != null) {
            slot.setType(request.type());
        }
        if (request.floor() != null) {
            slot.setFloor(request.floor());
        }
        slotRepository.save(slot);
        broadcastBoard();
        return toResponse(slot);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public SlotResponse updateStatus(Long id, SlotStatusRequest request) {
        ParkingSlot slot = findSlot(id);
        SlotStatus target = request.status();
        if (target == SlotStatus.RESERVED || target == SlotStatus.OCCUPIED) {
            throw ApiException.badRequest("Slots become RESERVED/OCCUPIED through the reservation flow");
        }
        if (target == slot.getStatus()) {
            return toResponse(slot);
        }
        if (reservationRepository.existsBySlotAndStatus(slot, ReservationStatus.ACTIVE)) {
            throw ApiException.conflict("Slot " + slot.getSlotNumber()
                    + " has an active reservation. Check the vehicle out first.");
        }
        slot.setStatus(target);
        if (target == SlotStatus.MAINTENANCE) {
            slot.setMaintenanceNote(request.note());
            activityService.publish("MAINTENANCE", slot,
                    "Slot " + slot.getSlotNumber() + " sent to maintenance"
                            + (request.note() != null && !request.note().isBlank()
                            ? " — " + request.note().trim() : ""));
        } else {
            slot.setMaintenanceNote(null);
            activityService.publish("AVAILABLE", slot,
                    "Slot " + slot.getSlotNumber() + " is back in service");
        }
        broadcastBoard();
        return toResponse(slot);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteSlot(Long id) {
        ParkingSlot slot = findSlot(id);
        if (reservationRepository.existsBySlotAndStatus(slot, ReservationStatus.ACTIVE)) {
            throw ApiException.conflict("Slot " + slot.getSlotNumber()
                    + " has an active reservation. Check the vehicle out first.");
        }
        slotRepository.delete(slot);
        broadcastBoard();
    }

    /**
     * Live session details for one slot. Vehicle plates are masked unless the
     * viewer is an admin or attendant.
     */
    @Transactional(readOnly = true)
    public SlotSessionResponse slotSession(Long slotId, String viewerEmail) {
        ParkingSlot slot = findSlot(slotId);
        User viewer = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        boolean staff = viewer.getRole() == Role.ADMIN || viewer.getRole() == Role.ATTENDANT;

        Optional<Reservation> active = reservationRepository.findBySlotAndStatus(slot, ReservationStatus.ACTIVE);
        if (active.isEmpty()) {
            return new SlotSessionResponse(slot.getStatus(), null, null, null,
                    0, 0, 0, null, slot.getMaintenanceNote());
        }
        Reservation reservation = active.get();
        String vehicle = staff
                ? reservation.getVehicleNumber()
                : maskPlate(reservation.getVehicleNumber());
        long elapsedMinutes = reservation.getEntryTime() != null
                ? Duration.between(reservation.getEntryTime(), Instant.now()).toMinutes()
                : 0;
        Double currentCost = null;
        if (reservation.getEntryTime() != null) {
            long committedMinutes = (reservation.getExpectedHours() + reservation.getExtensionHours()) * 60L;
            long billableMinutes = Math.max(Math.max(1, elapsedMinutes), committedMinutes);
            double hours = Math.ceil(billableMinutes / 60.0);
            currentCost = Math.round(slot.getHourlyRate() * hours * 100.0) / 100.0;
        }
        return new SlotSessionResponse(
                slot.getStatus(),
                vehicle,
                reservation.getEntryTime(),
                reservation.getExpiresAt(),
                reservation.getExpectedHours(),
                reservation.getExtensionHours(),
                elapsedMinutes,
                currentCost,
                slot.getMaintenanceNote());
    }

    @Transactional(readOnly = true)
    public ParkingSlot findSlot(Long id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Slot not found"));
    }

    public void broadcastBoard() {
        notifier.broadcastSlotBoard(toBoard(slotRepository.findAll(slotSort())));
    }

    private Sort slotSort() {
        return Sort.by("floor").and(Sort.by("zone")).and(Sort.by("slotNumber"));
    }

    private String maskPlate(String plate) {
        if (plate == null || plate.length() <= 2) {
            return "••";
        }
        return "•".repeat(plate.length() - 2) + plate.substring(plate.length() - 2);
    }

    private SlotBoardResponse toBoard(List<ParkingSlot> slots) {
        List<SlotResponse> slotResponses = slots.stream().map(this::toResponse).toList();
        long available = slots.stream().filter(s -> s.getStatus() == SlotStatus.AVAILABLE).count();
        long reserved = slots.stream().filter(s -> s.getStatus() == SlotStatus.RESERVED).count();
        long occupied = slots.stream().filter(s -> s.getStatus() == SlotStatus.OCCUPIED).count();
        long maintenance = slots.stream().filter(s -> s.getStatus() == SlotStatus.MAINTENANCE).count();
        return new SlotBoardResponse(slotResponses, slots.size(), available, reserved, occupied, maintenance);
    }

    private SlotResponse toResponse(ParkingSlot slot) {
        return new SlotResponse(slot.getId(), slot.getSlotNumber(), slot.getZone(),
                slot.getStatus(), slot.getHourlyRate(), slot.getType(),
                slot.getFloor(), slot.getMaintenanceNote());
    }
}
