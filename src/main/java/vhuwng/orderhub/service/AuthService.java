package vhuwng.orderhub.service;

import vhuwng.orderhub.dto.request.LoginRequestDto;
import vhuwng.orderhub.dto.request.RegisterRequestDto;
import vhuwng.orderhub.dto.response.MeResponseDto;
import vhuwng.orderhub.util.IssuedTokens;

public interface AuthService {
    void register(RegisterRequestDto request);

    IssuedTokens login(LoginRequestDto request);

    IssuedTokens refresh(String refreshToken);

    MeResponseDto getCurrentUser();
}
