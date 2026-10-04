package vhuwng.orderhub.dto.response;

import vhuwng.orderhub.entity.UserEntity;

public record MeResponseDto(
        Long id,
        String username,
        String fullName,
        String role
) {
    public static MeResponseDto fromEntity(UserEntity user) {
        return new MeResponseDto(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}
