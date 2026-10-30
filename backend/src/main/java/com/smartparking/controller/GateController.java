package com.smartparking.controller;

import com.smartparking.dto.*;
import com.smartparking.service.GateService;
import com.smartparking.service.GateStateService;
import com.smartparking.service.SensorSimulatorService;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.model.SlotStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;
    private final GateStateService gateState;
    private final SensorSimulatorService simulator;
    private final ParkingSlotRepository slotRepository;

    /** Snapshot of the barrier counters vs actual slot occupancy. */
    @GetMapping("/status")
    public GateStatusResponse status() {
        return new GateStatusResponse(
                gateState.getEntries(),
                gateState.getExits(),
                gateState.getInsideByGates(),
                slotRepository.countByStatus(SlotStatus.OCCUPIED),
                gateState.getAnomalies(),
                simulator.isEnabled(),
                simulator.parkedBotCount(),
                simulator.getTargetOccupancy());
    }

    /** ANPR entry: the barrier camera reports a plate; the gate decides. */
    @PostMapping("/entry")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ReservationResponse entry(@Valid @RequestBody GateVehicleRequest request) {
        return gateService.entry(request.vehicleNumber());
    }

    /** Exit: settle the session and open the barrier. */
    @PostMapping("/exit")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ReservationResponse exit(@Valid @RequestBody GateVehicleRequest request) {
        return gateService.exit(request.vehicleNumber());
    }
}
