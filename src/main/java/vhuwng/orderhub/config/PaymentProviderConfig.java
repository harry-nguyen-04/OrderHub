package vhuwng.orderhub.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import vhuwng.orderhub.properties.PaymentProperties;

@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentProviderConfig {

    @Bean
    public RestClient paymentProviderRestClient(PaymentProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.providerBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public Retry paymentProviderRetry(PaymentProperties properties) {
        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        properties.retryInitialBackoff().toMillis(), 2.0))
                .retryOnException(exception -> exception instanceof ResourceAccessException
                        || exception instanceof HttpServerErrorException)
                .build();
        return Retry.of("payment-provider", retryConfig);
    }

    @Bean
    public TimeLimiter paymentProviderTimeLimiter(PaymentProperties properties) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(properties.timeLimit())
                .cancelRunningFuture(true)
                .build();
        return TimeLimiter.of("payment-provider", config);
    }

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService paymentProviderExecutor() {
        return Executors.newFixedThreadPool(4, Thread.ofPlatform().name("payment-provider-").factory());
    }
}
