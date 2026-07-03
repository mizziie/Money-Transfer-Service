package com.banking.mts.service;

import com.banking.mts.domain.entity.Account;
import com.banking.mts.domain.entity.Transfer;
import com.banking.mts.domain.enums.AccountStatus;
import com.banking.mts.dto.CreateTransferRequest;
import com.banking.mts.dto.TransferResponse;
import com.banking.mts.dto.TransferResult;
import com.banking.mts.repository.AccountRepository;
import com.banking.mts.repository.LedgerEntryRepository;
import com.banking.mts.repository.OutboxEventRepository;
import com.banking.mts.repository.TransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private DistributedLock distributedLock;
    @Mock
    private AccountService accountService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TransferService transferService;

    @Test
    void createTransfer_insufficientBalance_throws422() {
        Account from = account(1L, 100, AccountStatus.ACTIVE);
        Account to = account(2L, 50, AccountStatus.ACTIVE);

        when(idempotencyService.check(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(to));
        when(transferRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        CreateTransferRequest request = CreateTransferRequest.builder()
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount("200")
                .currency("THB")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> transferService.createTransfer(request, "idem-key"));
        assertTrue(ex.getMessage().contains("Insufficient balance"));
    }

    @Test
    void createTransfer_inactiveAccount_throws422() {
        Account from = account(1L, 1000, AccountStatus.FROZEN);
        Account to = account(2L, 50, AccountStatus.ACTIVE);

        when(idempotencyService.check(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(to));
        when(transferRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        CreateTransferRequest request = CreateTransferRequest.builder()
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount("100")
                .currency("THB")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> transferService.createTransfer(request, "idem-key"));
        assertTrue(ex.getMessage().contains("active"));
    }

    @Test
    void createTransfer_selfTransfer_throws422() {
        Account from = account(1L, 1000, AccountStatus.ACTIVE);

        when(idempotencyService.check(anyString(), anyString())).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(from));
        when(transferRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        CreateTransferRequest request = CreateTransferRequest.builder()
                .fromAccountId(1L)
                .toAccountId(1L)
                .amount("100")
                .currency("THB")
                .build();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> transferService.createTransfer(request, "idem-key"));
        assertTrue(ex.getMessage().contains("Self-transfer"));
    }

    @Test
    void createTransfer_redisReplay_returnsSameTransfer() {
        TransferResult cached = TransferResult.builder()
                .response(TransferResponse.builder()
                        .transferId(5L)
                        .status("COMPLETED")
                        .fromAccountId(1L)
                        .toAccountId(2L)
                        .amount(BigDecimal.valueOf(100))
                        .currency("THB")
                        .build())
                .replay(true)
                .build();

        when(idempotencyService.check(anyString(), anyString())).thenReturn(Optional.of(cached));

        CreateTransferRequest request = CreateTransferRequest.builder()
                .fromAccountId(1L)
                .toAccountId(2L)
                .amount("100")
                .currency("THB")
                .build();

        TransferResult result = transferService.createTransfer(request, "idem-key");

        assertTrue(result.isReplay());
        assertEquals(5L, result.getResponse().getTransferId());
        verifyNoInteractions(accountRepository);
    }

    private Account account(Long id, long balance, AccountStatus status) {
        Account account = Account.builder()
                .id(id)
                .accountNumber(String.format("%010d", id))
                .ownerName("owner" + id)
                .balance(BigDecimal.valueOf(balance))
                .currency("THB")
                .status(status)
                .build();
        return account;
    }
}
