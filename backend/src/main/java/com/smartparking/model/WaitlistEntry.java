package com.smartparking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "waitlist_entries")
@Getter
@Setter
@NoArgsConstructor
public class WaitlistEntry {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitlistStatus status = WaitlistStatus.QUEUED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant fulfilledAt;

    public WaitlistEntry(User user, String vehicleNumber, String zone, SlotType slotType) {
        this.user = user;
        this.vehicleNumber = vehicleNumber;
        this.zone = zone;
        this.slotType = slotType;
    }
}
