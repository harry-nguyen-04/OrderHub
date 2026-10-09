package vhuwng.orderhub.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import vhuwng.orderhub.dto.request.LoginRequestDto;
import vhuwng.orderhub.dto.request.RegisterRequestDto;
import vhuwng.orderhub.dto.response.AuthResponseDto;
import vhuwng.orderhub.dto.response.MeResponseDto;
import vhuwng.orderhub.dto.response.MessageResponseDto;
import vhuwng.orderhub.middleware.exception.UnauthorizedException;
import vhuwng.orderhub.properties.JwtProperties;
import vhuwng.orderhub.service.AuthService;
import vhuwng.orderhub.util.IssuedTokens;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String REFRESH_COOKIE = "refreshToken";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponseDto> register(@Valid @RequestBody RegisterRequestDto request) {
        authService.register(request);
        return ResponseEntity.ok(new MessageResponseDto("Account created successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletResponse response
    ) {
        IssuedTokens tokens = authService.login(request);
        writeRefreshCookie(response, tokens.refreshToken(), jwtProperties.getRefreshTokenExpiration().toSeconds());
        return ResponseEntity.ok(AuthResponseDto.bearer(tokens.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        try {
            IssuedTokens tokens = authService.refresh(refreshToken);
            writeRefreshCookie(response, tokens.refreshToken(), jwtProperties.getRefreshTokenExpiration().toSeconds());
            return ResponseEntity.ok(AuthResponseDto.bearer(tokens.accessToken()));
        } catch (UnauthorizedException ex) {
            writeRefreshCookie(response, "", 0);
            throw ex;
        }
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponseDto> me() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    private void writeRefreshCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
