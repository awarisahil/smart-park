package com.smartparking.controller;

import com.smartparking.dto.SlotBoardResponse;
import com.smartparking.dto.SlotRequest;
import com.smartparking.dto.SlotResponse;
import com.smartparking.dto.SlotSessionResponse;
import com.smartparking.dto.SlotStatusRequest;
import com.smartparking.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    @GetMapping
    public SlotBoardResponse getBoard() {
        return slotService.getBoard();
    }

    @GetMapping("/{id}/session")
    public SlotSessionResponse slotSession(@PathVariable Long id, Authentication authentication) {
        return slotService.slotSession(id, authentication.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotResponse createSlot(@Valid @RequestBody SlotRequest request) {
        return slotService.createSlot(request);
    }

    @PatchMapping("/{id}/status")
    public SlotResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody SlotStatusRequest request) {
        return slotService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(@PathVariable Long id) {
        slotService.deleteSlot(id);
    }
}
