package com.smartparking.repository;

import com.smartparking.model.User;
import com.smartparking.model.VehicleProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleProfileRepository extends JpaRepository<VehicleProfile, Long> {

    List<VehicleProfile> findByUserOrderByCreatedAtAsc(User user);

    boolean existsByUserAndVehicleNumber(User user, String vehicleNumber);
}
