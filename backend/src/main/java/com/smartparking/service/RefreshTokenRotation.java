package com.smartparking.service;

import com.smartparking.model.User;
import com.smartparking.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token revocations must commit independently of the request
 * transaction — the caller may throw right after (e.g. token-reuse detection),
 * which would otherwise roll the revocation back.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenRotation {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamily(Long userId) {
        refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .forEach(token -> token.setRevoked(true));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeOne(String tokenHash) {
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> token.setRevoked(true));
    }

    /** Entry point for the reuse path — must commit independently of the caller. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeFamilyOf(User user) {
        refreshTokenRepository.findAll().stream()
                .filter(token -> token.getUser().getId().equals(user.getId()))
                .forEach(token -> token.setRevoked(true));
    }
}
