package com.smartparking.dto;

public record VehicleResponse(
        Long id,
        String label,
        String vehicleNumber
) {
}
