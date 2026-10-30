package com.smartparking.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parking_slots")
@Getter
@Setter
@NoArgsConstructor
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slotNumber;

    @Column(nullable = false)
    private String zone;

    /** Floor/level number — 1 is the ground floor. */
    @Column(nullable = false)
    private int floor = 1;

    @Column(nullable = false)
    private double hourlyRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status = SlotStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotType type = SlotType.STANDARD;

    private String maintenanceNote;

    public ParkingSlot(String slotNumber, String zone, double hourlyRate) {
        this.slotNumber = slotNumber;
        this.zone = zone;
        this.hourlyRate = hourlyRate;
    }

    public ParkingSlot(String slotNumber, String zone, double hourlyRate, SlotType type, int floor) {
        this(slotNumber, zone, hourlyRate);
        this.type = type;
        this.floor = floor;
    }
}
