package com.aurora.notification.listener;

import com.aurora.notification.email.EmailService;
import com.aurora.notification.sms.SmsService;
import com.aurora.notification.store.NotificationStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
                new ObjectMapper(), emailService, smsService, store, new ProcessedEventTracker(),
                "http://localhost:4200/reset-password", "http://localhost:4200/verify-email");
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

    private static final String PAYMENT_CONFIRMED_EVENT = """
            {"eventId":"pc-1","orderNumber":"AUR-1001","customerEmail":"a@b.com",
             "customerName":"Ada","amount":99.90,"currency":"USD"}
            """;

    private static final String PAYMENT_FAILED_EVENT = """
            {"eventId":"pf-1","orderNumber":"AUR-1001","customerEmail":"a@b.com",
             "customerName":"Ada","amount":99.90,"currency":"USD","reason":"Card declined"}
            """;

    @Test
    void sendsAConfirmationEmailForAPaymentConfirmedEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onPaymentConfirmed(PAYMENT_CONFIRMED_EVENT);

        verify(emailService, times(1)).send(any(), any(), any());
        assertThat(store.count()).isEqualTo(1);
        assertThat(store.findRecent().get(0).type()).isEqualTo("PAYMENT_CONFIRMED");
    }

    @Test
    void doesNotResendARedeliveredPaymentConfirmedEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onPaymentConfirmed(PAYMENT_CONFIRMED_EVENT);
        listener.onPaymentConfirmed(PAYMENT_CONFIRMED_EVENT); // duplicate delivery

        verify(emailService, times(1)).send(any(), any(), any());
        assertThat(store.count()).isEqualTo(1);
    }

    @Test
    void sendsAFailureEmailForAPaymentFailedEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onPaymentFailed(PAYMENT_FAILED_EVENT);

        verify(emailService, times(1)).send(any(), any(), any());
        assertThat(store.findRecent().get(0).type()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void treatsAMalformedPaymentPayloadAsNonRetryable() {
        assertThatThrownBy(() -> listener.onPaymentConfirmed("{ broken"))
                .isInstanceOf(NonRetryableEventException.class);
    }

    private static final String PASSWORD_RESET_EVENT = """
            {"eventId":"pr-1","userId":"00000000-0000-0000-0000-000000000001",
             "customerEmail":"a@b.com","customerName":"Ada",
             "resetToken":"rid.secret","expiresInMinutes":30}
            """;

    @Test
    void sendsAResetEmailWithTheLinkForAPasswordResetEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onPasswordResetRequested(PASSWORD_RESET_EVENT);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("a@b.com"), eq("Reset your Aurora Marketplace password"), body.capture());
        // The link host is composed here; the body carries it, never just the bare token.
        assertThat(body.getValue()).contains("http://localhost:4200/reset-password?token=rid.secret");
        verify(smsService, never()).send(any(), any()); // email-only
        assertThat(store.findRecent().get(0).type()).isEqualTo("PASSWORD_RESET");
    }

    @Test
    void doesNotResendARedeliveredPasswordResetEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onPasswordResetRequested(PASSWORD_RESET_EVENT);
        listener.onPasswordResetRequested(PASSWORD_RESET_EVENT); // duplicate delivery

        verify(emailService, times(1)).send(any(), any(), any());
    }

    @Test
    void treatsAMalformedResetPayloadAsNonRetryable() {
        assertThatThrownBy(() -> listener.onPasswordResetRequested("{ broken"))
                .isInstanceOf(NonRetryableEventException.class);
    }

    @Test
    void rethrowsWhenResetEmailDeliveryFailsSoItCanBeRetried() {
        when(emailService.send(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> listener.onPasswordResetRequested(PASSWORD_RESET_EVENT))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store.count()).isZero();
    }

    private static final String EMAIL_VERIFICATION_EVENT = """
            {"eventId":"ev-1","userId":"00000000-0000-0000-0000-000000000002",
             "customerEmail":"new@aurora.test","customerName":"Newbie",
             "verificationToken":"ev.secret","expiresInMinutes":1440}
            """;

    @Test
    void sendsAVerificationEmailWithTheLinkForAVerificationEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onEmailVerificationRequested(EMAIL_VERIFICATION_EVENT);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("new@aurora.test"), eq("Verify your Aurora Marketplace email"), body.capture());
        assertThat(body.getValue()).contains("http://localhost:4200/verify-email?token=ev.secret");
        verify(smsService, never()).send(any(), any()); // email-only
        assertThat(store.findRecent().get(0).type()).isEqualTo("EMAIL_VERIFICATION");
    }

    @Test
    void doesNotResendARedeliveredVerificationEvent() {
        when(emailService.send(any(), any(), any())).thenReturn(true);

        listener.onEmailVerificationRequested(EMAIL_VERIFICATION_EVENT);
        listener.onEmailVerificationRequested(EMAIL_VERIFICATION_EVENT); // duplicate delivery

        verify(emailService, times(1)).send(any(), any(), any());
    }

    @Test
    void treatsAMalformedVerificationPayloadAsNonRetryable() {
        assertThatThrownBy(() -> listener.onEmailVerificationRequested("{ broken"))
                .isInstanceOf(NonRetryableEventException.class);
    }
}
