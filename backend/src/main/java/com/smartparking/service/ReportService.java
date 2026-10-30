package com.smartparking.service;

import com.smartparking.dto.UsageReportResponse;
import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.SlotStatus;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ParkingSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UsageReportResponse usageReport() {
        List<ParkingSlot> slots = slotRepository.findAll();
        long total = slots.size();
        long available = countByStatus(slots, SlotStatus.AVAILABLE);
        long reserved = countByStatus(slots, SlotStatus.RESERVED);
        long occupied = countByStatus(slots, SlotStatus.OCCUPIED);
        long maintenance = countByStatus(slots, SlotStatus.MAINTENANCE);
        double occupancyRate = total == 0 ? 0 : ((reserved + occupied) * 100.0) / total;

        List<Reservation> completed = reservationRepository.findByStatusOrderByReservedAtDesc(ReservationStatus.COMPLETED);
        long active = reservationRepository.countByStatus(ReservationStatus.ACTIVE);
        long cancelled = reservationRepository.countByStatus(ReservationStatus.CANCELLED);
        double averageDuration = completed.stream()
                .filter(r -> r.getEntryTime() != null && r.getExitTime() != null)
                .mapToLong(r -> Duration.between(r.getEntryTime(), r.getExitTime()).toMinutes())
                .average()
                .orElse(0);

        double totalRevenue = completed.stream()
                .map(Reservation::getAmount)
                .filter(amount -> amount != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        long sessionsToday = completed.stream()
                .filter(r -> r.getEntryTime() != null && !r.getEntryTime().isBefore(startOfToday))
                .count();

        Map<String, Double> revenueByZone = completed.stream()
                .filter(r -> r.getAmount() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getSlot().getZone(),
                        Collectors.summingDouble(Reservation::getAmount)));
        double maxZoneRevenue = revenueByZone.values().stream().mapToDouble(v -> v).max().orElse(0);
        List<UsageReportResponse.ZoneRevenue> zoneRevenues = revenueByZone.entrySet().stream()
                .map(e -> new UsageReportResponse.ZoneRevenue(
                        e.getKey(),
                        Math.round(e.getValue() * 100.0) / 100.0))
                .sorted(Comparator.comparingDouble(UsageReportResponse.ZoneRevenue::revenue).reversed())
                .toList();

        Map<ParkingSlot, List<Reservation>> completedBySlot = completed.stream()
                .collect(Collectors.groupingBy(Reservation::getSlot));
        List<UsageReportResponse.SlotUtilization> utilizations = slots.stream()
                .map(slot -> {
                    List<Reservation> reservations = completedBySlot.getOrDefault(slot, List.of());
                    long minutes = reservations.stream()
                            .filter(r -> r.getEntryTime() != null && r.getExitTime() != null)
                            .mapToLong(r -> Duration.between(r.getEntryTime(), r.getExitTime()).toMinutes())
                            .sum();
                    return new UsageReportResponse.SlotUtilization(
                            slot.getSlotNumber(), slot.getZone(), reservations.size(), minutes);
                })
                .sorted(Comparator.comparingLong(UsageReportResponse.SlotUtilization::timesUsed).reversed())
                .toList();

        ZoneId zoneId = ZoneId.systemDefault();
        long[] hourly = new long[24];
        Map<LocalDate, double[]> daily = new TreeMap<>();
        LocalDate today = LocalDate.now(zoneId);
        for (Reservation reservation : completed) {
            if (reservation.getEntryTime() == null) {
                continue;
            }
            LocalDateTime entry = LocalDateTime.ofInstant(reservation.getEntryTime(), zoneId);
            hourly[entry.getHour()]++;
            if (!entry.toLocalDate().isBefore(today.minusDays(6))) {
                double[] acc = daily.computeIfAbsent(entry.toLocalDate(), k -> new double[2]);
                if (reservation.getAmount() != null) {
                    acc[0] += reservation.getAmount();
                }
                acc[1] += 1;
            }
        }
        List<UsageReportResponse.HourStat> hourlyOccupancy = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourlyOccupancy.add(new UsageReportResponse.HourStat(hour, hourly[hour]));
        }
        List<UsageReportResponse.DailyStat> dailyTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            double[] acc = daily.getOrDefault(date, new double[2]);
            dailyTrend.add(new UsageReportResponse.DailyStat(
                    date.toString(),
                    Math.round(acc[0] * 100.0) / 100.0,
                    (long) acc[1]));
        }

        return new UsageReportResponse(
                total, available, reserved, occupied, maintenance,
                Math.round(occupancyRate * 100.0) / 100.0,
                active, completed.size(), cancelled,
                Math.round(averageDuration * 100.0) / 100.0,
                Math.round(totalRevenue * 100.0) / 100.0,
                sessionsToday,
                zoneRevenues,
                hourlyOccupancy,
                dailyTrend,
                utilizations);
    }

    private long countByStatus(List<ParkingSlot> slots, SlotStatus status) {
        return slots.stream().filter(s -> s.getStatus() == status).count();
    }
}
