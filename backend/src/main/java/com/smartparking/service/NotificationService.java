package com.smartparking.service;

import com.smartparking.dto.NotificationResponse;
import com.smartparking.dto.NotificationSummary;
import com.smartparking.exception.ApiException;
import com.smartparking.model.Notification;
import com.smartparking.model.NotificationType;
import com.smartparking.model.User;
import com.smartparking.repository.NotificationRepository;
import com.smartparking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public void notifyUser(User user, NotificationType type, String message) {
        notificationRepository.save(new Notification(user, type, message));
    }

    @Transactional(readOnly = true)
    public NotificationSummary myNotifications(String email) {
        User user = findUser(email);
        List<NotificationResponse> items = notificationRepository
                .findTop20ByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
        long unread = notificationRepository.countByUserAndReadFalse(user);
        return new NotificationSummary(unread, items);
    }

    @Transactional
    public void markAllRead(String email) {
        notificationRepository.markAllReadForUser(findUser(email));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
