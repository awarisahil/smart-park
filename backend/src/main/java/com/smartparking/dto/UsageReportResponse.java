package com.smartparking.dto;

import java.util.List;

public record UsageReportResponse(
        long totalSlots,
        long available,
        long reserved,
        long occupied,
        long maintenance,
        double occupancyRatePercent,
        long activeReservations,
        long completedReservations,
        long cancelledReservations,
        double averageDurationMinutes,
        double totalRevenue,
        long sessionsToday,
        List<ZoneRevenue> zoneRevenues,
        List<HourStat> hourlyOccupancy,
        List<DailyStat> dailyTrend,
        List<SlotUtilization> slotUtilizations
) {

    public record ZoneRevenue(
            String zone,
            double revenue
    ) {
    }

    /** Check-in counts per hour of day (index 0-23), across completed sessions. */
    public record HourStat(
            int hour,
            long checkIns
    ) {
    }

    /** Revenue and session counts for one day. */
    public record DailyStat(
            String date,
            double revenue,
            long sessions
    ) {
    }

    public record SlotUtilization(
            String slotNumber,
            String zone,
            long timesUsed,
            long totalMinutesUsed
    ) {
    }
}
