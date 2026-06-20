package com.aurora.backend.user.service;

import com.aurora.backend.auth.dto.AuthUserResponse;
import com.aurora.backend.common.exception.BusinessException;
import com.aurora.backend.user.dto.NotificationPreferenceRequest;
import com.aurora.backend.user.entity.User;
import com.aurora.backend.user.notification.NotificationChannel;
import com.aurora.backend.user.repository.UserRepository;
import com.aurora.backend.user.role.Role;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private UserRepository userRepository;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        accountService = new AccountService(userRepository);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updatesToSmsWhenAPhoneIsProvided() {
        User user = new User("ada@aurora.test", "hash", "Ada", "Lovelace", Role.CUSTOMER, true);

        AuthUserResponse response = accountService.updateNotificationPreference(
                user, new NotificationPreferenceRequest(NotificationChannel.SMS, "+34123456789"));

        assertThat(response.notificationChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(response.phone()).isEqualTo("+34123456789");
        verify(userRepository).save(user);
    }

    @Test
    void rejectsSmsWithoutAPhoneAsBadRequest() {
        User user = new User("ada@aurora.test", "hash", "Ada", "Lovelace", Role.CUSTOMER, true);

        assertThatThrownBy(() -> accountService.updateNotificationPreference(
                user, new NotificationPreferenceRequest(NotificationChannel.SMS, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    void switchingToEmailNeedsNoPhone() {
        User user = new User("ada@aurora.test", "hash", "Ada", "Lovelace", Role.CUSTOMER, true);

        AuthUserResponse response = accountService.updateNotificationPreference(
                user, new NotificationPreferenceRequest(NotificationChannel.EMAIL, null));

        assertThat(response.notificationChannel()).isEqualTo(NotificationChannel.EMAIL);
    }
}
