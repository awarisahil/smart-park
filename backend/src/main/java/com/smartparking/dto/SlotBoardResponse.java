package com.smartparking.dto;

import java.util.List;

public record SlotBoardResponse(
        List<SlotResponse> slots,
        long total,
        long available,
        long reserved,
        long occupied,
        long maintenance
) {
}
