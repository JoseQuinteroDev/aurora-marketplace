package com.aurora.notification.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default SMS transport for local development: writes the message to the log
 * instead of calling a paid provider. Active unless
 * {@code app.notification.sms.provider} selects another transport.
 */
@Component
@ConditionalOnProperty(name = "app.notification.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsTransport implements SmsTransport {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsTransport.class);

    @Override
    public boolean send(String from, String to, String message) {
        log.info("[SMS:log] from={} to={} message=\"{}\"", from, to, message);
        return true;
    }

    @Override
    public String name() {
        return "log";
    }
}
