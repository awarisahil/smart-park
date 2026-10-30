package com.smartparking.dto;

public record GateStatusResponse(
        long entries,
        long exits,
        long insideByGates,
        long occupiedSlots,
        int anomalies,
        boolean simulatorEnabled,
        long simulatedVehiclesInside,
        double targetOccupancy
) {
}
