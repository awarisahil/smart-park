package com.smartparking.controller;

import com.smartparking.dto.WaitlistRequest;
import com.smartparking.dto.WaitlistResponse;
import com.smartparking.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistResponse join(Authentication authentication,
                                 @Valid @RequestBody WaitlistRequest request) {
        return waitlistService.join(authentication.getName(), request);
    }

    @GetMapping("/my")
    public WaitlistResponse myPosition(Authentication authentication) {
        return waitlistService.myPosition(authentication.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(Authentication authentication, @PathVariable Long id) {
        waitlistService.leave(authentication.getName(), id);
    }
}
