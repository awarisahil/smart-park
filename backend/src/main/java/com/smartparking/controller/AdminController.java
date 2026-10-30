package com.smartparking.controller;

import com.smartparking.dto.*;
import com.smartparking.exception.ApiException;
import com.smartparking.model.User;
import com.smartparking.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/users")
    public List<UserResponse> users() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getCreatedAt))
                .map(this::toResponse)
                .toList();
    }

    @PatchMapping("/users/{id}/role")
    public UserResponse updateRole(Authentication authentication, @PathVariable Long id,
                                   @Valid @RequestBody RoleUpdateRequest request) {
        User actor = findByEmail(authentication.getName());
        User target = findUser(id);
        if (actor.getId().equals(target.getId())) {
            throw ApiException.conflict("You cannot change your own role");
        }
        target.setRole(request.role());
        userRepository.save(target);
        return toResponse(target);
    }

    @PatchMapping("/users/{id}/enabled")
    public UserResponse setEnabled(Authentication authentication, @PathVariable Long id,
                                   @Valid @RequestBody EnabledUpdateRequest request) {
        User actor = findByEmail(authentication.getName());
        User target = findUser(id);
        if (actor.getId().equals(target.getId()) && !request.enabled()) {
            throw ApiException.conflict("You cannot disable your own account");
        }
        target.setEnabled(request.enabled());
        userRepository.save(target);
        return toResponse(target);
    }

    @PostMapping("/announcements")
    public AnnouncementResponse announce(@Valid @RequestBody AnnouncementRequest request) {
        AnnouncementResponse announcement = new AnnouncementResponse(request.message().trim(), Instant.now());
        messagingTemplate.convertAndSend("/topic/announcements", announcement);
        return announcement;
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
