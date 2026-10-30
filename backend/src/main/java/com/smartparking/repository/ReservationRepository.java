package com.smartparking.repository;

import com.smartparking.model.ParkingSlot;
import com.smartparking.model.Reservation;
import com.smartparking.model.ReservationStatus;
import com.smartparking.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long>,
        JpaSpecificationExecutor<Reservation> {

    List<Reservation> findByUserOrderByReservedAtDesc(User user);

    List<Reservation> findByStatusOrderByReservedAtDesc(ReservationStatus status);

    List<Reservation> findByStatusAndEntryTimeIsNullAndExpiresAtBefore(ReservationStatus status, Instant time);

    List<Reservation> findByStatusAndEntryTimeIsNullAndWarningSentFalseAndExpiresAtBefore(
            ReservationStatus status, Instant time);

    boolean existsByUserAndStatus(User user, ReservationStatus status);

    boolean existsByVehicleNumberAndStatus(String vehicleNumber, ReservationStatus status);

    java.util.Optional<Reservation> findFirstByVehicleNumberAndStatusOrderByReservedAtDesc(
            String vehicleNumber, ReservationStatus status);

    List<Reservation> findBySyntheticTrueAndStatusAndEntryTimeIsNotNull(ReservationStatus status);

    boolean existsBySlotAndStatus(ParkingSlot slot, ReservationStatus status);

    java.util.Optional<Reservation> findBySlotAndStatus(ParkingSlot slot, ReservationStatus status);

    long countByStatus(ReservationStatus status);
}
