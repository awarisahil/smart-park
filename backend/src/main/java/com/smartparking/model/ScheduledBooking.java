package com.smartparking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An advance booking: the user schedules a parking window, and the allocation
 * engine assigns the best matching slot shortly before it starts — the slot is
 * not held all day, keeping utilization high.
 */
@Entity
@Table(name = "scheduled_bookings")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 15)
    private String vehicleNumber;

    private String zone;

    @Enumerated(EnumType.STRING)
    private SlotType slotType;

    /** When the driver wants to arrive. */
    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private int expectedHours;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.UPCOMING;

    /** Set once the engine has allocated a slot. */
    private Long reservationId;

    private String slotNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public ScheduledBooking(User user, String vehicleNumber, String zone,
                            SlotType slotType, Instant startTime, int expectedHours) {
        this.user = user;
        this.vehicleNumber = vehicleNumber;
        this.zone = zone;
        this.slotType = slotType;
        this.startTime = startTime;
        this.expectedHours = expectedHours;
    }
}
