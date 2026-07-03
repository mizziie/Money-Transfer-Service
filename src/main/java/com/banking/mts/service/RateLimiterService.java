package com.banking.mts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    public void check(String key, int limit, Duration window) {
        String redisKey = "rate_limit:transfer:" + key;
        Long current = redisTemplate.opsForValue().increment(redisKey);
        if (current == null) {
            // Redis unavailable: fail-closed
            throw new RuntimeException("Rate limit service unavailable");
        }
        if (current == 1) {
            redisTemplate.expire(redisKey, window);
        }
        if (current > limit) {
            throw new RuntimeException("Rate limit exceeded");
        }
    }
}
