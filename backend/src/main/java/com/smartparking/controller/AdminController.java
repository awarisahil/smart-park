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
    private final com.smartparking.service.AuditService auditService;
    private final com.smartparking.repository.AuditLogRepository auditLogRepository;

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
        auditService.record("ROLE_CHANGE", target.getEmail(), "role → " + request.role());
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
        auditService.record(request.enabled() ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED",
                target.getEmail(), "account " + (request.enabled() ? "enabled" : "disabled"));
        return toResponse(target);
    }

    @PostMapping("/announcements")
    public AnnouncementResponse announce(@Valid @RequestBody AnnouncementRequest request) {
        AnnouncementResponse announcement = new AnnouncementResponse(request.message().trim(), Instant.now());
        messagingTemplate.convertAndSend("/topic/announcements", announcement);
        auditService.record("ANNOUNCEMENT", null,
                "broadcast: " + request.message().trim());
        return announcement;
    }

    /** Paginated audit trail of security-relevant actions (newest first). */
    @GetMapping("/audit")
    public org.springframework.data.domain.Page<AuditLogResponse> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                page, size, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.findAll(pageable).map(this::toAuditResponse);
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

    private AuditLogResponse toAuditResponse(com.smartparking.model.AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorEmail(), log.getActorRole(),
                log.getAction(), log.getTarget(), log.getDetail(), log.getIp(), log.getCreatedAt());
    }
}
