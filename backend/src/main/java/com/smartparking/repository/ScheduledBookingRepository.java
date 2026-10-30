package com.smartparking.repository;

import com.smartparking.model.BookingStatus;
import com.smartparking.model.ScheduledBooking;
import com.smartparking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ScheduledBookingRepository extends JpaRepository<ScheduledBooking, Long> {

    List<ScheduledBooking> findByUserOrderByStartTimeAsc(User user);

    List<ScheduledBooking> findByStatusAndStartTimeBeforeOrderByStartTimeAsc(BookingStatus status, Instant time);

    long countByUserAndStatus(User user, BookingStatus status);

    boolean existsByUserAndVehicleNumberAndStatus(User user, String vehicleNumber, BookingStatus status);
}
