package com.aurora.backend.user.dto;

import com.aurora.backend.user.notification.NotificationChannel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Updates the caller's notification channel preference. A phone may be supplied
 * in the same request so a customer can switch to {@code SMS} and provide the
 * number they want to be texted on in one step. The phone format mirrors
 * registration; the SMS-requires-a-phone rule is enforced in the domain.
 */
public record NotificationPreferenceRequest(
        @NotNull(message = "Notification channel is required.")
        NotificationChannel channel,

        // Optional. When present, replaces the stored phone before applying the
        // preference. A blank value is treated as "leave the stored phone as is".
        @Size(max = 32, message = "Phone must be at most 32 characters.")
        @Pattern(
                regexp = "^$|^\\+?[0-9][0-9 ().-]{6,30}$",
                message = "Phone must be a valid number."
        )
        String phone
) {
}
