package com.aurora.backend.user.service;

import com.aurora.backend.auth.dto.AuthUserResponse;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.user.dto.NotificationPreferenceRequest;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service account operations for the authenticated customer. Today this is
 * the notification channel preference; the surface is intentionally narrow.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository userRepository;

    public AccountService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Applies a new notification preference for {@code user}. The domain rejects
     * choosing SMS without a deliverable phone; that surfaces as a 400 rather than
     * a 500. Returns the refreshed user so the client can update its stored copy.
     */
    @Transactional
    public AuthUserResponse updateNotificationPreference(User user, NotificationPreferenceRequest request) {
        try {
            user.updateNotificationPreference(request.channel(), normalizePhone(request.phone()));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PHONE_REQUIRED_FOR_SMS", ex.getMessage());
        }
        User saved = userRepository.save(user);
        log.info("Notification preference updated (email={}, channel={}).",
                saved.getEmail(), saved.getNotificationChannel());
        return AuthUserResponse.from(saved);
    }

    /** A blank phone means "leave the stored number unchanged"; anything else is trimmed. */
    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return phone.trim();
    }
}
