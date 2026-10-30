package com.smartparking.config;

import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.Role;
import com.smartparking.model.SlotStatus;
import com.smartparking.model.SlotType;
import com.smartparking.model.User;
import com.smartparking.model.VehicleProfile;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.ReservationRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.repository.VehicleProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final Map<String, Double> ZONE_RATES = Map.of(
            "A", 60.0, "B", 50.0, "C", 40.0, "D", 35.0, "E", 30.0, "F", 25.0);

    /** Zones per floor and the floor each zone lives on. */
    private static final Map<Integer, List<String>> FLOOR_ZONES = Map.of(
            1, List.of("A", "B"),
            2, List.of("C", "D"),
            3, List.of("E", "F"));

    private final UserRepository userRepository;
    private final ParkingSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final VehicleProfileRepository vehicleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        User demoUser = null;
        if (userRepository.count() == 0) {
            userRepository.save(new User("System Admin", "admin@parking.com",
                    passwordEncoder.encode("admin123"), Role.ADMIN));
            userRepository.save(new User("Gate Attendant", "attendant@parking.com",
                    passwordEncoder.encode("attendant123"), Role.ATTENDANT));
            demoUser = userRepository.save(new User("Demo User", "user@parking.com",
                    passwordEncoder.encode("user123"), Role.USER));
            log.info("Seeded demo accounts -> admin@parking.com/admin123, "
                    + "attendant@parking.com/attendant123, user@parking.com/user123");
        } else {
            demoUser = userRepository.findByEmail("user@parking.com").orElse(null);
        }

        if (slotRepository.count() == 0) {
            int created = 0;
            for (int floor = 1; floor <= 3; floor++) {
                for (String zone : FLOOR_ZONES.get(floor)) {
                    for (int i = 1; i <= 4; i++) {
                        ParkingSlot slot = new ParkingSlot(
                                zone + "-" + String.format("%02d", i), zone,
                                ZONE_RATES.get(zone), typeFor(zone, i), floor);
                        slotRepository.save(slot);
                        created++;
                    }
                }
            }
            // A couple of maintenance slots with realistic reasons.
            slotRepository.findBySlotNumber("D-01").ifPresent(slot -> {
                slot.setStatus(SlotStatus.MAINTENANCE);
                slot.setMaintenanceNote("Line painting in progress");
            });
            slotRepository.findBySlotNumber("F-02").ifPresent(slot -> {
                slot.setStatus(SlotStatus.MAINTENANCE);
                slot.setMaintenanceNote("Barrier sensor fault — technician booked");
            });
            log.info("Seeded {} slots across 3 floors (zones A-F, ₹25-₹60/hr) with typed slots",
                    created);
        }

        if (demoUser != null && vehicleRepository.count() == 0) {
            vehicleRepository.save(new VehicleProfile(demoUser, "My Car", "KA01AB1234"));
            vehicleRepository.save(new VehicleProfile(demoUser, "Work Bike", "KA01XY5678"));
            log.info("Seeded 2 vehicle profiles for the demo user");
        }

        // Account used for gate walk-ins and simulated sensor traffic.
        if (!userRepository.existsByEmail("walkin@parking.local")) {
            userRepository.save(new User("Walk-in / Auto", "walkin@parking.local",
                    passwordEncoder.encode(java.util.UUID.randomUUID().toString()), Role.USER));
            log.info("Seeded walk-in guest account for gate and simulator traffic");
        }

        if (reservationRepository.count() == 0 && demoUser != null) {
            seedHistory(demoUser);
        }
    }

    private SlotType typeFor(String zone, int index) {
        if (zone.equals("A") && index >= 3) return SlotType.EV_CHARGING;
        if (zone.equals("B") && index == 3) return SlotType.ACCESSIBLE;
        if (zone.equals("B") && index == 4) return SlotType.COMPACT;
        if (zone.equals("C") && index == 3) return SlotType.EV_CHARGING;
        if (zone.equals("C") && index == 4) return SlotType.ACCESSIBLE;
        if (zone.equals("D") && index == 3) return SlotType.COMPACT;
        if (zone.equals("E") && index == 3) return SlotType.EV_CHARGING;
        if (zone.equals("E") && index == 4) return SlotType.ACCESSIBLE;
        if (zone.equals("F") && index == 3) return SlotType.COMPACT;
        return SlotType.STANDARD;
    }

    /** A few completed sessions from the past days so reports look alive on first run. */
    private void seedHistory(User user) {
        List<ParkingSlot> slots = slotRepository.findAll();
        String[] vehicles = {"KA01AB1234", "MH12CD5678", "DL08EF9012", "KA01AB1234",
                "TN10GH3456", "KA01AB1234", "MH12CD5678", "UP32JK7890"};
        int[] slotIndexes = {0, 3, 8, 1, 12, 9, 5, 14};
        int[] durationsHours = {2, 4, 1, 3, 5, 2, 6, 3};

        Instant now = Instant.now();
        for (int i = 0; i < vehicles.length; i++) {
            ParkingSlot slot = slots.get(slotIndexes[i]);
            Instant entry = now.minus(5L - (i / 2), ChronoUnit.DAYS).minus(i * 37L, ChronoUnit.MINUTES);
            Instant exit = entry.plus(durationsHours[i], ChronoUnit.HOURS);
            Reservation reservation = new Reservation(user, slot, vehicles[i],
                    String.format("%06d", 100000 + i * 137));
            reservation.setStatus(ReservationStatus.COMPLETED);
            reservation.setReservedAt(entry.minus(10, ChronoUnit.MINUTES));
            reservation.setEntryTime(entry);
            reservation.setExitTime(exit);
            reservation.setExpectedHours(durationsHours[i]);
            reservation.setAmount(slot.getHourlyRate() * durationsHours[i]);
            reservationRepository.save(reservation);
        }
        log.info("Seeded 8 historical parking sessions for the demo user");
    }
}
