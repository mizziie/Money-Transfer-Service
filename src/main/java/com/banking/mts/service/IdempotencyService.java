package com.banking.mts.service;

import com.banking.mts.dto.TransferResponse;
import com.banking.mts.dto.TransferResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    public Optional<TransferResult> check(String idempotencyKey, String requestHash) {
        String json = redisTemplate.opsForValue().get(PREFIX + idempotencyKey);
        if (json == null) {
            return Optional.empty();
        }
        try {
            CachedEntry entry = objectMapper.readValue(json, CachedEntry.class);
            if (entry.getRequestHash().equals(requestHash)) {
                return Optional.of(TransferResult.builder()
                        .response(entry.getResponse())
                        .replay(true)
                        .build());
            }
            throw new RuntimeException("Idempotency conflict: " + idempotencyKey);
        } catch (JsonProcessingException e) {
            redisTemplate.delete(PREFIX + idempotencyKey);
            return Optional.empty();
        }
    }

    public void store(String idempotencyKey, String requestHash, TransferResponse response) {
        try {
            CachedEntry entry = CachedEntry.builder()
                    .requestHash(requestHash)
                    .response(response)
                    .build();
            redisTemplate.opsForValue().set(
                    PREFIX + idempotencyKey,
                    objectMapper.writeValueAsString(entry),
                    TTL);
        } catch (JsonProcessingException e) {
            // Best-effort cache; do not fail the transfer
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CachedEntry {
        private String requestHash;
        private TransferResponse response;
    }
}
