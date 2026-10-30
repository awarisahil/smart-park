package com.smartparking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reservations")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private ParkingSlot slot;

    @Column(nullable = false)
    private String vehicleNumber;

    /**
     * 6-digit code the driver shows at the gate; used by attendants
     * to look up the reservation.
     */
    @Column(nullable = false)
    private String entryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant reservedAt = Instant.now();

    /** The reservation is auto-cancelled if the vehicle has not entered by this time. */
    private Instant expiresAt;

    private Instant entryTime;

    private Instant exitTime;

    /** Amount billed at check-out (hourly rate, rounded up per hour). */
    private Double amount;

    /** Duration the user committed to when booking. */
    @Column(nullable = false)
    private int expectedHours = 2;

    /** Extra hours added while parked. */
    @Column(nullable = false)
    private int extensionHours = 0;

    /** Whether the 5-minute expiry warning notification has been sent. */
    @Column(nullable = false)
    private boolean warningSent = false;

    /** Session created outside the user app: simulator traffic or gate walk-ins. */
    @Column(nullable = false)
    private boolean synthetic = false;

    /** Session billed at ₹0 because a valid season pass covers it. */
    @Column(nullable = false)
    private boolean passCovered = false;

    public Reservation(User user, ParkingSlot slot, String vehicleNumber, String entryCode) {
        this.user = user;
        this.slot = slot;
        this.vehicleNumber = vehicleNumber;
        this.entryCode = entryCode;
    }
}
