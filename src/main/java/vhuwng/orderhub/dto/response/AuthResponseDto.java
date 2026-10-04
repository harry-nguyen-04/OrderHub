package vhuwng.orderhub.dto.response;

public record AuthResponseDto(
        String accessToken,
        String tokenType
) {
    public static AuthResponseDto bearer(String accessToken) {
        return new AuthResponseDto(accessToken, "Bearer");
    }
}
