package vhuwng.orderhub.service.impl;

import java.time.Instant;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import vhuwng.orderhub.dto.request.LoginRequestDto;
import vhuwng.orderhub.dto.request.RegisterRequestDto;
import vhuwng.orderhub.dto.response.MeResponseDto;
import vhuwng.orderhub.entity.RefreshTokenEntity;
import vhuwng.orderhub.entity.Role;
import vhuwng.orderhub.entity.UserEntity;
import vhuwng.orderhub.middleware.exception.DuplicateResourceException;
import vhuwng.orderhub.middleware.exception.UnauthorizedException;
import vhuwng.orderhub.repository.RefreshTokenRepository;
import vhuwng.orderhub.repository.UserRepository;
import vhuwng.orderhub.security.JwtService;
import vhuwng.orderhub.security.TokenHasher;
import vhuwng.orderhub.service.AuthService;
import vhuwng.orderhub.util.IssuedTokens;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {
    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String INVALID_REFRESH = "Invalid refresh token";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    public void register(RegisterRequestDto request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("User", "username", request.username());
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setHashPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(Role.USER);
        userRepository.save(user);
    }

    @Override
    public IssuedTokens login(LoginRequestDto request) {
        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getHashPassword())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return issueTokens(user);
    }

    @Override
    public IssuedTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh token is missing");
        }
        Claims claims = parseRefreshClaims(refreshToken);
        if (!JwtService.TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
            throw new UnauthorizedException(INVALID_REFRESH);
        }
        RefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(refreshToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH));
        if (stored.getRevokedAt() != null || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException(INVALID_REFRESH);
        }
        UserEntity user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH));
        if (!user.getUsername().equals(claims.getSubject())) {
            throw new UnauthorizedException(INVALID_REFRESH);
        }
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);
        return issueTokens(user);
    }

    @Override
    public MeResponseDto getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication is required");
        }
        UserEntity user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Authentication is required"));
        return MeResponseDto.fromEntity(user);
    }

    private IssuedTokens issueTokens(UserEntity user) {
        String accessToken = jwtService.generateAccessToken(user.getUsername(), user.getRole().name());
        JwtService.IssuedRefreshToken refreshToken = jwtService.generateRefreshToken(user.getUsername(), user.getRole().name());
        RefreshTokenEntity stored = new RefreshTokenEntity();
        stored.setUserId(user.getId());
        stored.setTokenHash(TokenHasher.sha256(refreshToken.token()));
        stored.setExpiresAt(refreshToken.expiresAt());
        refreshTokenRepository.save(stored);
        return new IssuedTokens(accessToken, refreshToken.token());
    }

    private Claims parseRefreshClaims(String refreshToken) {
        try {
            return jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new UnauthorizedException(INVALID_REFRESH);
        }
    }
}
