package com.banking.mts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:account:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    public LockToken acquire(Long accountId) {
        String key = LOCK_PREFIX + accountId;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, token, LOCK_TTL.toMillis(), TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(acquired)) {
            return new LockToken(key, token);
        }
        throw new LockAcquisitionException("Failed to acquire lock for account: " + accountId);
    }

    public void release(LockToken lockToken) {
        redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                        RELEASE_SCRIPT, Long.class),
                Collections.singletonList(lockToken.key()),
                lockToken.token()
        );
    }

    public void releaseAll(List<LockToken> locks) {
        locks.forEach(this::release);
    }

    public record LockToken(String key, String token) {
    }

    public static class LockAcquisitionException extends RuntimeException {
        public LockAcquisitionException(String message) {
            super(message);
        }
    }
}
