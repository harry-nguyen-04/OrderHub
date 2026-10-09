package vhuwng.orderhub.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import vhuwng.orderhub.middleware.exception.UnauthorizedException;
import vhuwng.orderhub.properties.PaymentProperties;

@Component
public class PaymentWebhookSignatureVerifier {
    private static final String PREFIX = "sha256=";
    private final PaymentProperties properties;

    public PaymentWebhookSignatureVerifier(PaymentProperties properties) {
        this.properties = properties;
    }

    public void verify(byte[] rawBody, String signatureHeader) {
        String secret = properties.webhookSecret();
        if (rawBody == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)
                || signatureHeader.length() != PREFIX.length() + 64 || secret == null || secret.isBlank()) {
            throw new UnauthorizedException("Invalid payment webhook signature");
        }

        try {
            byte[] provided = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody);
            if (!MessageDigest.isEqual(expected, provided)) {
                throw new UnauthorizedException("Invalid payment webhook signature");
            }
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid payment webhook signature");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
        }
    }
}
