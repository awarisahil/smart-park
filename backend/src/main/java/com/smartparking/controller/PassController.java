package com.smartparking.controller;

import com.smartparking.dto.PassRequest;
import com.smartparking.dto.PassResponse;
import com.smartparking.service.PassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/passes")
@RequiredArgsConstructor
public class PassController {

    private final PassService passService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PassResponse purchase(Authentication authentication,
                                 @Valid @RequestBody PassRequest request) {
        return passService.purchase(authentication.getName(), request);
    }

    @GetMapping("/my")
    public List<PassResponse> myPasses(Authentication authentication) {
        return passService.myPasses(authentication.getName());
    }
}
