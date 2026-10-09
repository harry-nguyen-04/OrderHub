package vhuwng.orderhub.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String providerBaseUrl,
        String webhookSecret,
        Duration connectTimeout,
        Duration readTimeout,
        Duration timeLimit,
        int maxAttempts,
        Duration retryInitialBackoff
) {
}
