package vhuwng.orderhub.dto.response;

public record IdempotencyResultDto<T>(int responseStatus, T body, boolean replayed) {
}
