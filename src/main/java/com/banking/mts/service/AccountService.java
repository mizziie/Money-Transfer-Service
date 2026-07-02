package com.banking.mts.service;

import com.banking.mts.domain.entity.Account;
import com.banking.mts.domain.entity.LedgerEntry;
import com.banking.mts.domain.enums.AccountStatus;
import com.banking.mts.domain.enums.EntryType;
import com.banking.mts.dto.*;
import com.banking.mts.repository.AccountRepository;
import com.banking.mts.repository.LedgerEntryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final DistributedLock distributedLock;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String ACCOUNT_CACHE_PREFIX = "account:";
    private static final long ACCOUNT_CACHE_TTL_SECONDS = 60;

    public AccountResponse getAccountById(Long id) {
        String cacheKey = ACCOUNT_CACHE_PREFIX + id;
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);

        Account account;
        if (cachedJson != null) {
            try {
                AccountCacheValue cached = objectMapper.readValue(cachedJson, AccountCacheValue.class);
                account = accountRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Account not found: " + id));
                return AccountResponse.builder()
                        .id(id)
                        .accountNumber(cached.accountNumber())
                        .ownerName(cached.ownerName())
                        .balance(account.getBalance())
                        .currency(cached.currency())
                        .status(cached.status())
                        .createdAt(account.getCreatedAt())
                        .updatedAt(account.getUpdatedAt())
                        .build();
            } catch (JsonProcessingException e) {
                // Cache corrupted, fall through to DB
                redisTemplate.delete(cacheKey);
            }
        }

        account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));
        cacheAccount(account);

        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerName(account.getOwnerName())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus().name())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }

    private void cacheAccount(Account account) {
        try {
            AccountCacheValue cacheValue = new AccountCacheValue(
                    account.getAccountNumber(),
                    account.getOwnerName(),
                    account.getCurrency(),
                    account.getStatus().name()
            );
            String json = objectMapper.writeValueAsString(cacheValue);
            redisTemplate.opsForValue().set(
                    ACCOUNT_CACHE_PREFIX + account.getId(),
                    json,
                    ACCOUNT_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (JsonProcessingException e) {
            // Ignore cache write errors
        }
    }

    public void invalidateAccountCache(Long accountId) {
        redisTemplate.delete(ACCOUNT_CACHE_PREFIX + accountId);
    }

    public AccountResponse createAccount(CreateAccountRequest request) {
        String tempAccountNumber = "TEMP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        Account account = Account.builder()
                .accountNumber(tempAccountNumber)
                .ownerName(request.getOwnerName())
                .balance(new BigDecimal(request.getInitialBalance()))
                .currency(request.getCurrency())
                .status(AccountStatus.ACTIVE)
                .build();

        Account savedAccount = accountRepository.save(account);
        savedAccount.setAccountNumber(String.format("%010d", savedAccount.getId()));
        accountRepository.save(savedAccount);

        return AccountResponse.builder()
                .id(savedAccount.getId())
                .accountNumber(savedAccount.getAccountNumber())
                .ownerName(savedAccount.getOwnerName())
                .balance(savedAccount.getBalance())
                .currency(savedAccount.getCurrency())
                .status(savedAccount.getStatus().name())
                .createdAt(savedAccount.getCreatedAt())
                .updatedAt(savedAccount.getUpdatedAt())
                .build();
    }

    public BalanceResponse getBalanceById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found: " + id));

        return BalanceResponse.builder()
                .accountId(account.getId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .asOf(LocalDateTime.now())
                .build();
    }

    public AccountOperationResponse deposit(Long accountId, DepositRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Account is not active: " + accountId);
        }

        account.setBalance(account.getBalance().add(request.getAmount()));
        Account savedAccount = accountRepository.save(account);

        LedgerEntry ledgerEntry = ledgerEntryRepository.save(LedgerEntry.builder()
                .accountId(savedAccount.getId())
                .entryType(EntryType.CREDIT)
                .amount(request.getAmount())
                .balanceAfter(savedAccount.getBalance())
                .build());

        invalidateAccountCache(savedAccount.getId());

        return AccountOperationResponse.builder()
                .accountId(savedAccount.getId())
                .balance(savedAccount.getBalance())
                .ledgerEntryId(ledgerEntry.getId())
                .build();
    }

    public AccountOperationResponse withdraw(Long accountId, WithdrawRequest request) {
        DistributedLock.LockToken lock = distributedLock.acquire(accountId);
        try {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new RuntimeException("Account is not active: " + accountId);
            }

            if (account.getBalance().compareTo(request.getAmount()) < 0) {
                throw new RuntimeException("Insufficient balance: " + accountId);
            }

            account.setBalance(account.getBalance().subtract(request.getAmount()));
            Account savedAccount = accountRepository.save(account);

            LedgerEntry ledgerEntry = ledgerEntryRepository.save(LedgerEntry.builder()
                    .accountId(savedAccount.getId())
                    .entryType(EntryType.DEBIT)
                    .amount(request.getAmount())
                    .balanceAfter(savedAccount.getBalance())
                    .build());

            invalidateAccountCache(savedAccount.getId());

            return AccountOperationResponse.builder()
                    .accountId(savedAccount.getId())
                    .balance(savedAccount.getBalance())
                    .ledgerEntryId(ledgerEntry.getId())
                    .build();
        } finally {
            distributedLock.release(lock);
        }
    }

    public AccountResponse changeStatus(Long accountId, ChangeAccountStatusRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

        AccountStatus newStatus;
        try {
            newStatus = AccountStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid account status: " + request.getStatus());
        }

        if (newStatus == AccountStatus.CLOSED && account.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Cannot close account with positive balance: " + accountId);
        }

        account.setStatus(newStatus);
        Account savedAccount = accountRepository.save(account);

        invalidateAccountCache(savedAccount.getId());

        return AccountResponse.builder()
                .id(savedAccount.getId())
                .accountNumber(savedAccount.getAccountNumber())
                .ownerName(savedAccount.getOwnerName())
                .balance(savedAccount.getBalance())
                .currency(savedAccount.getCurrency())
                .status(savedAccount.getStatus().name())
                .createdAt(savedAccount.getCreatedAt())
                .updatedAt(savedAccount.getUpdatedAt())
                .build();
    }

    public AccountStatementResponse getTransactions(Long accountId, int page, int size) {
        if (size > 100) {
            throw new RuntimeException("Page size must not exceed 100");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountId));

        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerEntry> ledgerPage = ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(account.getId(), pageable);

        List<AccountStatementResponse.TransactionItem> items = ledgerPage.getContent().stream()
                .map(this::mapToTransactionItem)
                .collect(Collectors.toList());

        return AccountStatementResponse.builder()
                .accountId(account.getId())
                .page(page)
                .size(size)
                .totalElements(ledgerPage.getTotalElements())
                .totalPages(ledgerPage.getTotalPages())
                .items(items)
                .build();
    }

    private AccountStatementResponse.TransactionItem mapToTransactionItem(LedgerEntry ledgerEntry) {
        return AccountStatementResponse.TransactionItem.builder()
                .id(ledgerEntry.getId())
                .entryType(ledgerEntry.getEntryType())
                .amount(ledgerEntry.getAmount())
                .balanceAfter(ledgerEntry.getBalanceAfter())
                .transferId(ledgerEntry.getTransferId())
                .createdAt(ledgerEntry.getCreatedAt())
                .build();
    }

    public record AccountCacheValue(String accountNumber, String ownerName, String currency, String status) {
    }
}
