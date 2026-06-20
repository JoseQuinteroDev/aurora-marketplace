package com.aurora.backend.user.entity;

import com.aurora.backend.user.notification.NotificationChannel;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Unit tests for the notification-channel preference logic on {@link User}: the
 * SMS-requires-a-phone rule and the deliverable-channel resolution. Pure domain
 * logic — no Spring context or database.
 */
class UserNotificationPreferenceTest {

    private User userWithoutPhone() {
        return new User("ada@aurora.test", "hash", "Ada", "Lovelace", Role.CUSTOMER, true);
    }

    private User userWithPhone() {
        return new User("ada@aurora.test", "hash", "Ada", "Lovelace", "+34123456789", Role.CUSTOMER, true);
    }

    @Test
    void defaultsToEmail() {
        User user = userWithoutPhone();

        assertThat(user.getNotificationChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(user.resolveNotificationChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    void switchesToSmsWhenAPhoneIsAlreadyOnFile() {
        User user = userWithPhone();

        user.updateNotificationPreference(NotificationChannel.SMS, null);

        assertThat(user.getNotificationChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(user.resolveNotificationChannel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void switchesToSmsWhenAPhoneIsSuppliedInTheSameStep() {
        User user = userWithoutPhone();

        user.updateNotificationPreference(NotificationChannel.SMS, "+34999888777");

        assertThat(user.getPhone()).isEqualTo("+34999888777");
        assertThat(user.resolveNotificationChannel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void rejectsSmsWithoutAnyPhone() {
        User user = userWithoutPhone();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> user.updateNotificationPreference(NotificationChannel.SMS, null));

        // The rejected change must not have taken effect.
        assertThat(user.getNotificationChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    void allowsSwitchingBackToEmailWithoutAPhone() {
        User user = userWithoutPhone();

        assertThatNoException()
                .isThrownBy(() -> user.updateNotificationPreference(NotificationChannel.EMAIL, null));
        assertThat(user.getNotificationChannel()).isEqualTo(NotificationChannel.EMAIL);
    }
}
