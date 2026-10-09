package vhuwng.orderhub.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import vhuwng.orderhub.properties.JwtProperties;

@Component
public class JwtService {
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";

    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String generateAccessToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAccessTokenExpiration());
        return buildToken(username, role, TYPE_ACCESS, null, now, expiresAt);
    }

    public IssuedRefreshToken generateRefreshToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getRefreshTokenExpiration());
        String token = buildToken(username, role, TYPE_REFRESH, UUID.randomUUID().toString(), now, expiresAt);
        return new IssuedRefreshToken(token, expiresAt);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String buildToken(String username, String role, String type, String id, Instant issuedAt, Instant expiresAt) {
        var builder = Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
        if (id != null) {
            builder.id(id);
        }
        return builder.signWith(signingKey()).compact();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public record IssuedRefreshToken(String token, Instant expiresAt) {
    }
}
