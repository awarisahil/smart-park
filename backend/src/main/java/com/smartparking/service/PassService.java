package com.smartparking.service;

import com.smartparking.dto.PassRequest;
import com.smartparking.dto.PassResponse;
import com.smartparking.exception.ApiException;
import com.smartparking.model.NotificationType;
import com.smartparking.model.PassStatus;
import com.smartparking.model.SeasonPass;
import com.smartparking.model.User;
import com.smartparking.repository.SeasonPassRepository;
import com.smartparking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PassService {

    /** Monthly base price by zone scope; null key = all zones. */
    private static final Map<String, Double> MONTHLY_BASE = Map.of(
            "A", 4000.0, "B", 3200.0, "C", 2500.0, "D", 2200.0, "E", 1800.0, "F", 1500.0);
    private static final double ANY_ZONE_MONTHLY = 3000.0;

    private final SeasonPassRepository passRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public static double priceFor(String zone, int months) {
        double monthly = zone == null ? ANY_ZONE_MONTHLY
                : MONTHLY_BASE.getOrDefault(zone.toUpperCase(), ANY_ZONE_MONTHLY);
        double discount = months >= 12 ? 0.8 : months >= 3 ? 0.9 : 1.0;
        return Math.round(monthly * months * discount * 100.0) / 100.0;
    }

    @Transactional
    public PassResponse purchase(String email, PassRequest request) {
        User user = findUser(email);
        String zone = request.zone() == null || request.zone().isBlank()
                ? null : request.zone().trim().toUpperCase();
        Instant now = Instant.now();
        boolean overlapping = passRepository.findByUserAndStatus(user, PassStatus.ACTIVE).stream()
                .anyMatch(pass -> !now.isAfter(pass.getValidUntil())
                        && (pass.getZone() == null || zone == null || pass.getZone().equals(zone)));
        if (overlapping) {
            throw ApiException.conflict("You already have an active pass for this scope");
        }
        double price = priceFor(zone, request.months());
        Instant validUntil = now.atZone(java.time.ZoneOffset.UTC)
                .plusMonths(request.months())
                .toInstant();
        SeasonPass pass = new SeasonPass(user, zone, now, validUntil, price);
        passRepository.save(pass);
        notificationService.notifyUser(user, NotificationType.SUCCESS,
                "Season pass purchased — " + (zone == null ? "all zones" : "Zone " + zone)
                        + ", valid until " + pass.getValidUntil() + ", ₹"
                        + String.format("%.2f", price));
        return toResponse(pass);
    }

    @Transactional(readOnly = true)
    public List<PassResponse> myPasses(String email) {
        return passRepository.findByUserOrderByCreatedAtDesc(findUser(email))
                .stream().map(this::toResponse).toList();
    }

    /** Does this user hold a pass covering the given zone right now? */
    @Transactional(readOnly = true)
    public boolean hasValidPass(User user, String slotZone) {
        Instant now = Instant.now();
        return passRepository.findByUserAndStatus(user, PassStatus.ACTIVE).stream()
                .anyMatch(pass -> pass.isValidAt(now, slotZone));
    }

    private PassResponse toResponse(SeasonPass pass) {
        return new PassResponse(
                pass.getId(),
                pass.getZone(),
                pass.getValidFrom(),
                pass.getValidUntil(),
                pass.getPrice(),
                pass.getStatus(),
                pass.isValidAt(Instant.now(), pass.getZone() == null ? "A" : pass.getZone()));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
