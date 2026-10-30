package com.smartparking.service;

import com.smartparking.dto.VehicleRequest;
import com.smartparking.dto.VehicleResponse;
import com.smartparking.exception.ApiException;
import com.smartparking.model.User;
import com.smartparking.model.VehicleProfile;
import com.smartparking.repository.UserRepository;
import com.smartparking.repository.VehicleProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final int MAX_VEHICLES_PER_USER = 5;

    private final VehicleProfileRepository vehicleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<VehicleResponse> myVehicles(String email) {
        return vehicleRepository.findByUserOrderByCreatedAtAsc(findUser(email))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public VehicleResponse addVehicle(String email, VehicleRequest request) {
        User user = findUser(email);
        String number = request.vehicleNumber().trim().toUpperCase();
        if (vehicleRepository.existsByUserAndVehicleNumber(user, number)) {
            throw ApiException.conflict("Vehicle " + number + " is already saved");
        }
        if (vehicleRepository.findByUserOrderByCreatedAtAsc(user).size() >= MAX_VEHICLES_PER_USER) {
            throw ApiException.badRequest("You can save up to " + MAX_VEHICLES_PER_USER + " vehicles");
        }
        VehicleProfile saved = vehicleRepository.save(
                new VehicleProfile(user, request.label().trim(), number));
        return toResponse(saved);
    }

    @Transactional
    public void deleteVehicle(String email, Long id) {
        VehicleProfile vehicle = vehicleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        if (!vehicle.getUser().getEmail().equalsIgnoreCase(email)) {
            throw ApiException.forbidden("You can only delete your own vehicles");
        }
        vehicleRepository.delete(vehicle);
    }

    private VehicleResponse toResponse(VehicleProfile vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getLabel(), vehicle.getVehicleNumber());
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
