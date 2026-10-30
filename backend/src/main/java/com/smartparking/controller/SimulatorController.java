package com.smartparking.controller;

import com.smartparking.dto.SimulatorToggleRequest;
import com.smartparking.service.SensorSimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

    private final SensorSimulatorService simulator;

    @PostMapping("/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> toggle(@Valid @RequestBody SimulatorToggleRequest request) {
        simulator.setEnabled(request.enabled());
        return Map.of("enabled", simulator.isEnabled());
    }

    /** Run one simulation cycle immediately — handy for demos. */
    @PostMapping("/tick")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> tick() {
        simulator.runCycle();
        return Map.of("enabled", simulator.isEnabled(), "simulatedInside", simulator.parkedBotCount());
    }
}
