package vhuwng.orderhub.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seaweedfs")
public record SeaweedFsProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        String basePath
) {
}
