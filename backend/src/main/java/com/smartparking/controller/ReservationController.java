package com.smartparking.controller;

import com.smartparking.dto.ExtensionRequest;
import com.smartparking.dto.ReservationRequest;
import com.smartparking.dto.ReservationResponse;
import com.smartparking.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(Authentication authentication,
                                       @Valid @RequestBody ReservationRequest request) {
        return reservationService.reserve(authentication.getName(), request);
    }

    @GetMapping("/my")
    public List<ReservationResponse> myReservations(Authentication authentication) {
        return reservationService.myReservations(authentication.getName());
    }

    @GetMapping("/my/stats")
    public com.smartparking.dto.MyStatsResponse myStats(Authentication authentication) {
        return reservationService.myStats(authentication.getName());
    }

    @GetMapping
    public List<ReservationResponse> activeReservations() {
        return reservationService.allReservations();
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(Authentication authentication, @PathVariable Long id) {
        return reservationService.cancel(authentication.getName(), id);
    }

    @PostMapping("/{id}/check-in")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ReservationResponse checkIn(@PathVariable Long id) {
        return reservationService.checkIn(id);
    }

    @PostMapping("/{id}/check-out")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ReservationResponse checkOut(@PathVariable Long id) {
        return reservationService.checkOut(id);
    }

    @PostMapping("/{id}/extend")
    public ReservationResponse extend(Authentication authentication, @PathVariable Long id,
                                      @Valid @RequestBody ExtensionRequest request) {
        return reservationService.extend(authentication.getName(), id, request);
    }

    @GetMapping("/my/export")
    public org.springframework.http.ResponseEntity<byte[]> exportMine(Authentication authentication) {
        byte[] csv = reservationService.myExportCsv(authentication.getName());
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=my-parking-history.csv")
                .body(csv);
    }
}
