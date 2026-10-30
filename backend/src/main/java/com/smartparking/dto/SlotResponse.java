package com.smartparking.dto;

import com.smartparking.model.SlotStatus;
import com.smartparking.model.SlotType;

public record SlotResponse(
        Long id,
        String slotNumber,
        String zone,
        SlotStatus status,
        double hourlyRate,
        SlotType type,
        int floor,
        String maintenanceNote
) {
}
