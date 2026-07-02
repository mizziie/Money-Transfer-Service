package com.banking.mts.service;

import com.banking.mts.domain.entity.Account;
import com.banking.mts.domain.enums.AccountStatus;
import com.banking.mts.dto.AccountResponse;
import com.banking.mts.dto.BalanceResponse;
import com.banking.mts.dto.CreateAccountRequest;
import com.banking.mts.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountResponse getAccountByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
        
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

    public AccountResponse createAccount(CreateAccountRequest request) {
        if (accountRepository.existsByAccountNumber(request.getAccountNumber())) {
            throw new RuntimeException("Account already exists: " + request.getAccountNumber());
        }

        Account account = Account.builder()
                .accountNumber(request.getAccountNumber())
                .ownerName(request.getOwnerName())
                .balance(new BigDecimal(request.getInitialBalance()))
                .currency(request.getCurrency())
                .status(AccountStatus.ACTIVE)
                .build();

        Account savedAccount = accountRepository.save(account);

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

    public BalanceResponse getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found: " + accountNumber));
        
        return BalanceResponse.builder()
                .accountNumber(account.getAccountNumber())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .build();
    }
}
