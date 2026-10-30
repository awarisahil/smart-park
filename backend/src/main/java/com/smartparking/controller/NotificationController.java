package com.smartparking.controller;

import com.smartparking.dto.NotificationSummary;
import com.smartparking.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/my")
    public NotificationSummary myNotifications(Authentication authentication) {
        return notificationService.myNotifications(authentication.getName());
    }

    @PostMapping("/read-all")
    public NotificationSummary markAllRead(Authentication authentication) {
        notificationService.markAllRead(authentication.getName());
        return notificationService.myNotifications(authentication.getName());
    }
}
