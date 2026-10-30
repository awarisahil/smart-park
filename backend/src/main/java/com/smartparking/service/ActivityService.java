package com.smartparking.service;

import com.smartparking.dto.ActivityEvent;
import com.smartparking.model.ParkingSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Transient live-activity feed: every meaningful slot event is broadcast to
 * /topic/activity and the most recent entries are kept in memory so clients
 * joining late can catch up via GET /api/activity.
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final int MAX_RECENT = 40;

    private final SimpMessagingTemplate messagingTemplate;
    private final Deque<ActivityEvent> recent = new ConcurrentLinkedDeque<>();

    public void publish(String type, ParkingSlot slot, String message) {
        ActivityEvent event = new ActivityEvent(
                type,
                slot.getSlotNumber(),
                slot.getZone(),
                slot.getFloor(),
                message,
                Instant.now());
        push(event);
    }

    /** Events that are not tied to a specific slot (gate anomalies, etc.). */
    public void publish(String type, String message) {
        push(new ActivityEvent(type, null, null, 0, message, Instant.now()));
    }

    private void push(ActivityEvent event) {
        recent.addLast(event);
        while (recent.size() > MAX_RECENT) {
            recent.pollFirst();
        }
        messagingTemplate.convertAndSend("/topic/activity", event);
    }

    /** Newest first. */
    public List<ActivityEvent> recentEvents() {
        List<ActivityEvent> all = new ArrayList<>(recent);
        java.util.Collections.reverse(all);
        return all;
    }
}
