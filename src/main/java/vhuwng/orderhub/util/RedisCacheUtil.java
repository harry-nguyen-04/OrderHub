package vhuwng.orderhub.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisCacheUtil {
    private static final Logger log = LoggerFactory.getLogger(RedisCacheUtil.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheUtil(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Object get(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            CacheValue cached = objectMapper.readValue(json, CacheValue.class);
            Class<?> type = Class.forName(cached.type());
            if (cached.page()) {
                JavaType pageType = objectMapper.getTypeFactory()
                        .constructParametricType(CachedPage.class, type);
                CachedPage<?> page = objectMapper.readValue(cached.data(), pageType);
                return new PageImpl<>(page.content(), PageRequest.of(page.number(), page.size()), page.totalElements());
            }
            return objectMapper.readValue(cached.data(), type);
        } catch (RuntimeException | ClassNotFoundException ex) {
            log.warn("Could not read Redis cache key {}", key, ex);
            return null;
        }
    }

    public void put(String key, Object value, long ttlSeconds) {
        if (value == null || ttlSeconds <= 0) {
            return;
        }
        try {
            CacheValue cached;
            if (value instanceof Page<?> page) {
                Class<?> itemType = page.isEmpty() ? Object.class : page.getContent().get(0).getClass();
                cached = new CacheValue(itemType.getName(), true,
                        objectMapper.writeValueAsString(CachedPage.from(page)));
            } else {
                cached = new CacheValue(value.getClass().getName(), false,
                        objectMapper.writeValueAsString(value));
            }
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(cached), Duration.ofSeconds(ttlSeconds));
        } catch (RuntimeException ex) {
            log.warn("Could not write Redis cache key {}", key, ex);
        }
    }

    public void deleteByPrefix(String prefix) {
        afterCommit(() -> {
            try {
                List<String> keys = new ArrayList<>();
                ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(100).build();
                try (Cursor<String> cursor = redisTemplate.scan(options)) {
                    cursor.forEachRemaining(keys::add);
                }
                if (!keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (RuntimeException ex) {
                log.warn("Could not delete Redis cache keys with prefix {}", prefix, ex);
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    public record CacheValue(String type, boolean page, String data) {
    }

    public record CachedPage<T>(List<T> content, int number, int size, long totalElements) {
        public static <T> CachedPage<T> from(Page<T> page) {
            return new CachedPage<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        }
    }
}
