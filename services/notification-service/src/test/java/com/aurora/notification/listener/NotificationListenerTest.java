package com.aurora.notification.listener;

import com.aurora.notification.email.EmailService;
import com.aurora.notification.sms.SmsService;
import com.aurora.notification.store.NotificationStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationListenerTest {

    private EmailService emailService;
    private SmsService smsService;
    private NotificationStore store;
    private NotificationListener listener;

    // No channel field → defaults to the email path (backward compatible).
    private static final String ORDER_EVENT = """
            {"eventId":"evt-1","orderNumber":"AUR-1001","customerEmail":"a@b.com",
             "customerName":"Ada","itemCount":2,"total":99.90,"currency":"USD"}
            """;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        smsService = mock(SmsService.class);
        store = new NotificationStore();
        listener = new NotificationListener(
                new ObjectMapper(), emailService, smsService, store, new ProcessedEventTracker());
    }

    @Test
    void sendsOneEmailForAValidOrderEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onOrderCreated(ORDER_EVENT);

        verify(emailService, times(1)).send(any(), any(), any());
        verify(smsService, never()).send(any(), any());
        assertThat(store.count()).isEqualTo(1);
        assertThat(store.findRecent().get(0).channel()).isEqualTo("EMAIL");
        assertThat(store.findRecent().get(0).status()).isEqualTo("SENT");
    }

    @Test
    void doesNotResendWhenTheSameEventIsRedelivered() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onOrderCreated(ORDER_EVENT);
        listener.onOrderCreated(ORDER_EVENT); // duplicate delivery

        verify(emailService, times(1)).send(any(), any(), any());
        assertThat(store.count()).isEqualTo(1);
    }

    @Test
    void rethrowsWhenEmailDeliveryFailsSoTheRecordCanBeRetried() {
        when(emailService.send(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> listener.onOrderCreated(ORDER_EVENT))
                .isInstanceOf(IllegalStateException.class);

        // Nothing recorded and the id is NOT marked processed, so a retry can run.
        assertThat(store.count()).isZero();
    }

    @Test
    void treatsMalformedPayloadAsNonRetryable() {
        assertThatThrownBy(() -> listener.onOrderCreated("{ this is not json"))
                .isInstanceOf(NonRetryableEventException.class);
    }

    @Test
    void deliversBySmsAndNotEmailWhenSmsIsTheChosenChannelAndAPhoneExists() {
        when(smsService.send(any(), any())).thenReturn(true);
        when(smsService.transportName()).thenReturn("log");

        String smsOrder = """
                {"eventId":"evt-2","orderNumber":"AUR-1002","customerEmail":"a@b.com",
                 "customerName":"Ada","customerPhone":"+34123456789","notificationChannel":"SMS",
                 "itemCount":1,"total":10.00,"currency":"USD"}
                """;

        listener.onOrderCreated(smsOrder);

        verify(smsService, times(1)).send(eq("+34123456789"), any());
        verify(emailService, never()).send(any(), any(), any());
        assertThat(store.count()).isEqualTo(1);
        assertThat(store.findRecent().get(0).channel()).isEqualTo("SMS");
        // The dev "log" transport only logs, so the recorded status is honest about it.
        assertThat(store.findRecent().get(0).status()).isEqualTo("LOGGED");
    }

    @Test
    void fallsBackToEmailWhenSmsIsChosenButNoPhoneIsAvailable() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        String smsOrderNoPhone = """
                {"eventId":"evt-3","orderNumber":"AUR-1003","customerEmail":"a@b.com",
                 "customerName":"Ada","notificationChannel":"SMS",
                 "itemCount":1,"total":10.00,"currency":"USD"}
                """;

        listener.onOrderCreated(smsOrderNoPhone);

        verify(emailService, times(1)).send(any(), any(), any());
        verify(smsService, never()).send(any(), any());
        assertThat(store.count()).isEqualTo(1);
        assertThat(store.findRecent().get(0).channel()).isEqualTo("EMAIL");
    }

    @Test
    void rethrowsWhenSmsDeliveryFailsOnTheChosenChannel() {
        when(smsService.send(any(), any())).thenReturn(false);

        String smsOrder = """
                {"eventId":"evt-4","orderNumber":"AUR-1004","customerEmail":"a@b.com",
                 "customerName":"Ada","customerPhone":"+34123456789","notificationChannel":"SMS",
                 "itemCount":1,"total":10.00,"currency":"USD"}
                """;

        assertThatThrownBy(() -> listener.onOrderCreated(smsOrder))
                .isInstanceOf(IllegalStateException.class);

        verify(emailService, never()).send(any(), any(), any());
        assertThat(store.count()).isZero();
    }
}
