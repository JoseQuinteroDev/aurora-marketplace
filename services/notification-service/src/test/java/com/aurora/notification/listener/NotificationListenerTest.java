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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationListenerTest {

    private EmailService emailService;
    private SmsService smsService;
    private NotificationStore store;
    private NotificationListener listener;

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
        assertThat(store.count()).isEqualTo(1);
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
    void rethrowsWhenDeliveryFailsSoTheRecordCanBeRetried() {
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
    void alsoSendsAnSmsWhenTheOrderHasAPhone() {
        when(emailService.send(any(), any(), any())).thenReturn(true);
        when(smsService.send(any(), any())).thenReturn(true);

        String orderWithPhone = """
                {"eventId":"evt-2","orderNumber":"AUR-1002","customerEmail":"a@b.com",
                 "customerName":"Ada","customerPhone":"+34123456789","itemCount":1,
                 "total":10.00,"currency":"USD"}
                """;

        listener.onOrderCreated(orderWithPhone);

        verify(emailService, times(1)).send(any(), any(), any());
        verify(smsService, times(1)).send(eq("+34123456789"), any());
        // One EMAIL record + one SMS record.
        assertThat(store.count()).isEqualTo(2);
    }

    @Test
    void sendsNoSmsWhenTheOrderHasNoPhone() {
        when(emailService.send(any(), any(), any())).thenReturn(true);
        when(smsService.send(any(), any())).thenReturn(false); // no phone → transport not engaged

        listener.onOrderCreated(ORDER_EVENT);

        // Email still sent; only the EMAIL record exists.
        verify(emailService, times(1)).send(any(), any(), any());
        assertThat(store.count()).isEqualTo(1);
    }
}
