package com.smartparking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "vehicle_profiles", uniqueConstraints =
        @UniqueConstraint(columnNames = {"user_id", "vehicleNumber"}))
@Getter
@Setter
@NoArgsConstructor
public class VehicleProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String label;

    @Column(nullable = false, length = 15)
    private String vehicleNumber;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public VehicleProfile(User user, String label, String vehicleNumber) {
        this.user = user;
        this.label = label;
        this.vehicleNumber = vehicleNumber;
    }
}
