package com.smartparking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Season/monthly pass: park as much as you like in the covered scope, billed ₹0. */
@Entity
@Table(name = "season_passes")
@Getter
@Setter
@NoArgsConstructor
public class SeasonPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** null = all zones. */
    private String zone;

    @Column(nullable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validUntil;

    @Column(nullable = false)
    private double price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PassStatus status = PassStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SeasonPass(User user, String zone, Instant validFrom, Instant validUntil, double price) {
        this.user = user;
        this.zone = zone;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.price = price;
    }

    public boolean isValidAt(Instant time, String slotZone) {
        return status == PassStatus.ACTIVE
                && !time.isBefore(validFrom)
                && !time.isAfter(validUntil)
                && (zone == null || zone.equalsIgnoreCase(slotZone));
    }
}
