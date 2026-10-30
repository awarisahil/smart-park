package com.smartparking.dto;

import com.smartparking.model.BookingStatus;
import com.smartparking.model.SlotType;

import java.time.Instant;

public record BookingResponse(
        Long id,
        String vehicleNumber,
        String zone,
        SlotType slotType,
        Instant startTime,
        int expectedHours,
        BookingStatus status,
        String slotNumber,
        Long reservationId
) {
}
