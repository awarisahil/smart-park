package com.smartparking.controller;

import com.smartparking.dto.ActivityEvent;
import com.smartparking.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /** Recent facility events, newest first (for clients joining after the fact). */
    @GetMapping
    public List<ActivityEvent> recent() {
        return activityService.recentEvents();
    }
}
