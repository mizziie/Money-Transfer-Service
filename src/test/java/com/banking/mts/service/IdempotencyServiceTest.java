package com.banking.mts.service;

import com.banking.mts.dto.TransferResponse;
import com.banking.mts.dto.TransferResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdempotencyServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final IdempotencyService service = new IdempotencyService(redisTemplate, objectMapper);

    @Test
    void cacheMiss_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotency:key-1")).thenReturn(null);

        Optional<TransferResult> result = service.check("key-1", "hash-1");

        assertTrue(result.isEmpty());
    }

    @Test
    void cacheHit_sameHash_returnsReplay() throws Exception {
        TransferResponse response = TransferResponse.builder()
                .transferId(1L)
                .status("COMPLETED")
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount(java.math.BigDecimal.valueOf(100))
                .currency("THB")
                .createdAt(LocalDateTime.now())
                .build();

        IdempotencyService.CachedEntry entry = IdempotencyService.CachedEntry.builder()
                .requestHash("hash-1")
                .response(response)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotency:key-1")).thenReturn(objectMapper.writeValueAsString(entry));

        Optional<TransferResult> result = service.check("key-1", "hash-1");

        assertTrue(result.isPresent());
        assertTrue(result.get().isReplay());
        assertEquals(1L, result.get().getResponse().getTransferId());
    }

    @Test
    void cacheHit_differentHash_throwsConflict() throws Exception {
        IdempotencyService.CachedEntry entry = IdempotencyService.CachedEntry.builder()
                .requestHash("hash-1")
                .response(TransferResponse.builder().build())
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("idempotency:key-1")).thenReturn(objectMapper.writeValueAsString(entry));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.check("key-1", "hash-2"));
        assertTrue(ex.getMessage().contains("Idempotency conflict"));
    }

    @Test
    void store_savesToRedisWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        TransferResponse response = TransferResponse.builder()
                .transferId(1L)
                .status("COMPLETED")
                .build();

        service.store("key-1", "hash-1", response);

        verify(valueOps).set(eq("idempotency:key-1"), anyString(), any());
    }
}
