package vhuwng.orderhub.annotation.redis;

import java.util.Arrays;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import vhuwng.orderhub.util.RedisCacheUtil;

@Aspect
@Component
public class RedisCacheAspect {
    private final RedisCacheUtil cacheUtil;

    public RedisCacheAspect(RedisCacheUtil cacheUtil) {
        this.cacheUtil = cacheUtil;
    }

    @Around("@annotation(redisCache)")
    public Object cache(
            ProceedingJoinPoint joinPoint,
            RedisCache redisCache
    ) throws Throwable {

        String cacheKey = redisCache.key() + ":" + Arrays.deepToString(joinPoint.getArgs());
        Object cached = cacheUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Object result = joinPoint.proceed();
        cacheUtil.put(cacheKey, result, redisCache.ttl());
        return result;
    }
}
