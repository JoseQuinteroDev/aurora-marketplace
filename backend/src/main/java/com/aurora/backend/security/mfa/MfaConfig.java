package com.aurora.backend.security.mfa;

import java.util.Base64;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MFA crypto: binds {@link MfaProperties} and exposes the {@link MfaSecretCipher} built
 * from the base64-decoded encryption key. {@link MfaKeyValidator} (a {@code @Component}) arms the
 * fail-fast check independently, mirroring how {@code JwtSecretValidator} guards the JWT secret.
 */
@Configuration
@EnableConfigurationProperties(MfaProperties.class)
public class MfaConfig {

    @Bean
    public MfaSecretCipher mfaSecretCipher(MfaProperties properties) {
        return new MfaSecretCipher(Base64.getDecoder().decode(properties.encryptionKey()));
    }
}
