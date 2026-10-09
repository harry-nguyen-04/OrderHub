package vhuwng.orderhub.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
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
import vhuwng.orderhub.util.IssuedTokens;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, refreshTokenRepository, passwordEncoder, jwtService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerHashesPasswordAndAssignsUserRole() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        authService.register(new RegisterRequestDto("alice", "password123", "Alice Nguyen"));

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(Role.USER, userCaptor.getValue().getRole());
        assertEquals("encoded-password", userCaptor.getValue().getHashPassword());
        assertNotEquals("password123", userCaptor.getValue().getHashPassword());
        verify(jwtService, never()).generateAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void registerDuplicateUsernameThrows() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> authService.register(new RegisterRequestDto("alice", "password123", "Alice Nguyen")));
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginReturnsTokensAndStoresRefreshHash() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        stubIssuedTokens();

        IssuedTokens tokens = authService.login(new LoginRequestDto("alice", "password123"));

        assertEquals("access-token", tokens.accessToken());
        assertEquals("refresh-token", tokens.refreshToken());
        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals(TokenHasher.sha256("refresh-token"), tokenCaptor.getValue().getTokenHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginUnknownUserThrows() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.login(new LoginRequestDto("alice", "password123")));
        verify(passwordEncoder, never()).matches(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void loginWrongPasswordThrows() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.login(new LoginRequestDto("alice", "wrong-password")));
        verify(jwtService, never()).generateAccessToken(any(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRotatesToken() {
        RefreshTokenEntity stored = activeRefreshToken();
        when(jwtService.parse("old-refresh")).thenReturn(refreshClaims("alice"));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("old-refresh"))).thenReturn(Optional.of(stored));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        stubIssuedTokens();

        IssuedTokens tokens = authService.refresh("old-refresh");

        assertEquals("access-token", tokens.accessToken());
        assertEquals("refresh-token", tokens.refreshToken());
        assertNotNull(stored.getRevokedAt());
        ArgumentCaptor<RefreshTokenEntity> tokenCaptor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository, times(2)).save(tokenCaptor.capture());
        assertEquals(TokenHasher.sha256("refresh-token"), tokenCaptor.getAllValues().get(1).getTokenHash());
    }

    @Test
    void refreshMissingTokenThrows() {
        assertThrows(UnauthorizedException.class, () -> authService.refresh(null));
        verify(jwtService, never()).parse(any());
    }

    @Test
    void refreshBlankTokenThrows() {
        assertThrows(UnauthorizedException.class, () -> authService.refresh("   "));
        verify(jwtService, never()).parse(any());
    }

    @Test
    void refreshUnknownTokenThrows() {
        when(jwtService.parse("old-refresh")).thenReturn(refreshClaims("alice"));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("old-refresh"))).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refresh("old-refresh"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRevokedTokenThrows() {
        RefreshTokenEntity stored = activeRefreshToken();
        stored.setRevokedAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(jwtService.parse("old-refresh")).thenReturn(refreshClaims("alice"));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("old-refresh"))).thenReturn(Optional.of(stored));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("old-refresh"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshExpiredTokenThrows() {
        RefreshTokenEntity stored = activeRefreshToken();
        stored.setExpiresAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(jwtService.parse("old-refresh")).thenReturn(refreshClaims("alice"));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("old-refresh"))).thenReturn(Optional.of(stored));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("old-refresh"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRejectsAccessToken() {
        when(jwtService.parse("access")).thenReturn(Jwts.claims()
                .subject("alice")
                .add(JwtService.CLAIM_TYPE, JwtService.TYPE_ACCESS)
                .build());

        assertThrows(UnauthorizedException.class, () -> authService.refresh("access"));
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void refreshInvalidJwtThrows() {
        when(jwtService.parse("bad")).thenThrow(new JwtException("bad"));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("bad"));
        verify(refreshTokenRepository, never()).findByTokenHash(any());
    }

    @Test
    void refreshUnknownUserThrows() {
        when(jwtService.parse("old-refresh")).thenReturn(refreshClaims("alice"));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("old-refresh")))
                .thenReturn(Optional.of(activeRefreshToken()));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refresh("old-refresh"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshSubjectMismatchThrows() {
        UserEntity user = user();
        user.setUsername("bob");
        when(jwtService.parse("old-refresh")).thenReturn(refreshClaims("alice"));
        when(refreshTokenRepository.findByTokenHash(TokenHasher.sha256("old-refresh")))
                .thenReturn(Optional.of(activeRefreshToken()));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> authService.refresh("old-refresh"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void getCurrentUserReturnsProfile() {
        UserEntity user = user();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, AuthorityUtils.createAuthorityList("ROLE_USER"))
        );

        MeResponseDto me = authService.getCurrentUser();

        assertEquals(1L, me.id());
        assertEquals("alice", me.username());
        assertEquals("Alice Nguyen", me.fullName());
        assertEquals("USER", me.role());
    }

    @Test
    void getCurrentUserWithoutAuthenticationThrows() {
        assertThrows(UnauthorizedException.class, () -> authService.getCurrentUser());
    }

    @Test
    void getCurrentUserAnonymousThrows() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
        );

        assertThrows(UnauthorizedException.class, () -> authService.getCurrentUser());
    }

    @Test
    void getCurrentUserMissingAccountThrows() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null, AuthorityUtils.createAuthorityList("ROLE_USER"))
        );

        assertThrows(UnauthorizedException.class, () -> authService.getCurrentUser());
    }

    private void stubIssuedTokens() {
        when(jwtService.generateAccessToken("alice", Role.USER.name())).thenReturn("access-token");
        when(jwtService.generateRefreshToken("alice", Role.USER.name()))
                .thenReturn(new JwtService.IssuedRefreshToken("refresh-token", Instant.parse("2030-01-01T00:00:00Z")));
    }

    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("alice");
        user.setHashPassword("hashed-password");
        user.setFullName("Alice Nguyen");
        user.setRole(Role.USER);
        return user;
    }

    private RefreshTokenEntity activeRefreshToken() {
        RefreshTokenEntity stored = new RefreshTokenEntity();
        stored.setUserId(1L);
        stored.setExpiresAt(Instant.parse("2030-01-01T00:00:00Z"));
        stored.setTokenHash(TokenHasher.sha256("old-refresh"));
        return stored;
    }

    private Claims refreshClaims(String subject) {
        return Jwts.claims()
                .subject(subject)
                .add(JwtService.CLAIM_TYPE, JwtService.TYPE_REFRESH)
                .build();
    }
}
