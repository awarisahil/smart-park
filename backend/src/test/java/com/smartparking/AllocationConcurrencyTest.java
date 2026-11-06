package com.smartparking;

import com.smartparking.dto.ReservationRequest;
import com.smartparking.dto.ReservationResponse;
import com.smartparking.model.Role;
import com.smartparking.model.User;
import com.smartparking.repository.ParkingSlotRepository;
import com.smartparking.repository.UserRepository;
import com.smartparking.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The allocation engine must never double-book: two concurrent reservations
 * race for the same "best" slot, and the database row lock must serialize them.
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
class AllocationConcurrencyTest {

    @Autowired
    ReservationService reservationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ParkingSlotRepository slotRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void concurrentReservationsGetDistinctSlots() throws Exception {
        long stamp = System.nanoTime() % 100000;
        User first = userRepository.save(
                new User("Conc One", "conc1" + stamp + "@test.com",
                        passwordEncoder.encode("pass123"), Role.USER));
        User second = userRepository.save(
                new User("Conc Two", "conc2" + stamp + "@test.com",
                        passwordEncoder.encode("pass123"), Role.USER));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<ReservationResponse> f1 = executor.submit(
                () -> reservationService.reserve(first.getEmail(),
                        new ReservationRequest("CCAA" + stamp, null, null, 1)));
        Future<ReservationResponse> f2 = executor.submit(
                () -> reservationService.reserve(second.getEmail(),
                        new ReservationRequest("CCBB" + stamp, null, null, 1)));

        ReservationResponse r1 = f1.get(30, TimeUnit.SECONDS);
        ReservationResponse r2 = f2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertNotNull(r1);
        assertNotNull(r2);
        assertNotEquals(r1.slotNumber(), r2.slotNumber(),
                "two concurrent reservations must never receive the same slot");

        // Cleanup so the suite's slot pool is unchanged.
        reservationService.cancel(first.getEmail(), r1.id());
        reservationService.cancel(second.getEmail(), r2.id());
    }
}
