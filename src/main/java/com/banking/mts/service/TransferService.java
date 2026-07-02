package com.banking.mts.service;

import com.banking.mts.domain.entity.Account;
import com.banking.mts.domain.entity.LedgerEntry;
import com.banking.mts.domain.entity.OutboxEvent;
import com.banking.mts.domain.entity.Transfer;
import com.banking.mts.domain.enums.AccountStatus;
import com.banking.mts.domain.enums.EntryType;
import com.banking.mts.domain.enums.OutboxStatus;
import com.banking.mts.domain.enums.TransferStatus;
import com.banking.mts.dto.CreateTransferRequest;
import com.banking.mts.dto.TransferResponse;
import com.banking.mts.dto.TransferResult;
import com.banking.mts.repository.AccountRepository;
import com.banking.mts.repository.LedgerEntryRepository;
import com.banking.mts.repository.OutboxEventRepository;
import com.banking.mts.repository.TransferRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DistributedLock distributedLock;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;

    public TransferResult createTransfer(CreateTransferRequest request, String idempotencyKey) {
        // Check idempotency
        String requestHash = generateRequestHash(request, idempotencyKey);
        java.util.Optional<Transfer> existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTransfer.isPresent()) {
            Transfer transfer = existingTransfer.get();
            if (transfer.getRequestHash().equals(requestHash)) {
                // Idempotent replay: same key + same payload
                return TransferResult.builder()
                        .response(TransferResponse.builder()
                                .transferId(transfer.getId())
                                .status(transfer.getStatus().name())
                                .fromAccountId(transfer.getFromAccountId())
                                .toAccountId(transfer.getToAccountId())
                                .amount(transfer.getAmount())
                                .currency(transfer.getCurrency())
                                .createdAt(transfer.getCreatedAt())
                                .build())
                        .replay(true)
                        .build();
            }
            throw new RuntimeException("Idempotency conflict: " + idempotencyKey);
        }

        BigDecimal amount = new BigDecimal(request.getAmount());

        // Validate accounts exist and business rules (before locking)
        Account fromAccount = accountRepository.findById(request.getFromAccountId())
                .orElseThrow(() -> new RuntimeException("From account not found: " + request.getFromAccountId()));

        Account toAccount = accountRepository.findById(request.getToAccountId())
                .orElseThrow(() -> new RuntimeException("To account not found: " + request.getToAccountId()));

        if (fromAccount.getStatus() != AccountStatus.ACTIVE || toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Both accounts must be active");
        }
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new RuntimeException("Self-transfer is not allowed");
        }
        if (!fromAccount.getCurrency().equals(request.getCurrency()) ||
            !toAccount.getCurrency().equals(request.getCurrency())) {
            throw new RuntimeException("Currency mismatch");
        }
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // Acquire locks in ascending order to prevent deadlock
        long firstId = Math.min(fromAccount.getId(), toAccount.getId());
        long secondId = Math.max(fromAccount.getId(), toAccount.getId());
        DistributedLock.LockToken firstLock = distributedLock.acquire(firstId);
        DistributedLock.LockToken secondLock = null;
        try {
            secondLock = distributedLock.acquire(secondId);

            // Re-fetch accounts under lock to get latest balance
            Account lockedFrom = accountRepository.findById(fromAccount.getId())
                    .orElseThrow(() -> new RuntimeException("From account not found: " + fromAccount.getId()));
            Account lockedTo = accountRepository.findById(toAccount.getId())
                    .orElseThrow(() -> new RuntimeException("To account not found: " + toAccount.getId()));

            if (lockedFrom.getBalance().compareTo(amount) < 0) {
                throw new RuntimeException("Insufficient balance");
            }

            // Create transfer record
            Transfer transfer = Transfer.builder()
                    .fromAccountId(lockedFrom.getId())
                    .toAccountId(lockedTo.getId())
                    .amount(amount)
                    .currency(request.getCurrency())
                    .status(TransferStatus.PENDING)
                    .idempotencyKey(idempotencyKey)
                    .requestHash(requestHash)
                    .build();
            Transfer savedTransfer = transferRepository.save(transfer);

            // Update balances
            lockedFrom.setBalance(lockedFrom.getBalance().subtract(amount));
            lockedTo.setBalance(lockedTo.getBalance().add(amount));
            accountRepository.save(lockedFrom);
            accountRepository.save(lockedTo);

            // Create ledger entries
            ledgerEntryRepository.save(LedgerEntry.builder()
                    .accountId(lockedFrom.getId())
                    .transferId(savedTransfer.getId())
                    .entryType(EntryType.DEBIT)
                    .amount(amount)
                    .balanceAfter(lockedFrom.getBalance())
                    .build());

            ledgerEntryRepository.save(LedgerEntry.builder()
                    .accountId(lockedTo.getId())
                    .transferId(savedTransfer.getId())
                    .entryType(EntryType.CREDIT)
                    .amount(amount)
                    .balanceAfter(lockedTo.getBalance())
                    .build());

            // Update transfer status
            savedTransfer.setStatus(TransferStatus.COMPLETED);
            transferRepository.save(savedTransfer);

            // Create outbox event for IBM MQ (same transaction)
            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType("Transfer")
                    .aggregateId(String.valueOf(savedTransfer.getId()))
                    .eventType("TransferCompleted")
                    .payload(buildTransferCompletedPayload(savedTransfer))
                    .status(OutboxStatus.PENDING)
                    .build());

            accountService.invalidateAccountCache(lockedFrom.getId());
            accountService.invalidateAccountCache(lockedTo.getId());

            return TransferResult.builder()
                    .response(TransferResponse.builder()
                            .transferId(savedTransfer.getId())
                            .status(savedTransfer.getStatus().name())
                            .fromAccountId(savedTransfer.getFromAccountId())
                            .toAccountId(savedTransfer.getToAccountId())
                            .amount(savedTransfer.getAmount())
                            .currency(savedTransfer.getCurrency())
                            .createdAt(savedTransfer.getCreatedAt())
                            .build())
                    .replay(false)
                    .build();

        } finally {
            if (secondLock != null) {
                distributedLock.release(secondLock);
            }
            distributedLock.release(firstLock);
        }
    }

    public TransferResponse getTransferById(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfer not found: " + id));

        return TransferResponse.builder()
                .transferId(transfer.getId())
                .status(transfer.getStatus().name())
                .fromAccountId(transfer.getFromAccountId())
                .toAccountId(transfer.getToAccountId())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency())
                .createdAt(transfer.getCreatedAt())
                .build();
    }

    private String generateRequestHash(CreateTransferRequest request, String idempotencyKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = request.getFromAccountId() + 
                          request.getToAccountId() + 
                          request.getAmount() + 
                          request.getCurrency() + 
                          idempotencyKey;
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate request hash", e);
        }
    }

    private String buildTransferCompletedPayload(Transfer transfer) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "eventId", java.util.UUID.randomUUID().toString(),
                    "eventType", "TransferCompleted",
                    "transferId", transfer.getId(),
                    "fromAccountId", transfer.getFromAccountId(),
                    "toAccountId", transfer.getToAccountId(),
                    "amount", transfer.getAmount(),
                    "currency", transfer.getCurrency(),
                    "occurredAt", LocalDateTime.now().toString()
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to build outbox event payload", e);
        }
    }
}
