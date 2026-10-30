package com.smartparking.repository;

import com.smartparking.model.ParkingSlot;
import com.smartparking.model.SlotStatus;
import com.smartparking.model.SlotType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {

    Optional<ParkingSlot> findBySlotNumber(String slotNumber);

    boolean existsBySlotNumber(String slotNumber);

    long countByStatus(SlotStatus status);

    /**
     * Select-for-update query used by the automated allocation engine.
     * Locking the candidate rows prevents two concurrent reservations
     * from grabbing the same slot. Ground floor fills first, then zone and
     * slot number. Zone and slot type are optional filters.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM ParkingSlot s
            WHERE s.status = :status
              AND (:zone IS NULL OR s.zone = :zone)
              AND (:type IS NULL OR s.type = :type)
            ORDER BY s.floor ASC, s.zone ASC, s.slotNumber ASC
            """)
    List<ParkingSlot> lockSlotsByStatusAndZoneAndType(@Param("status") SlotStatus status,
                                                      @Param("zone") String zone,
                                                      @Param("type") SlotType type);
}
