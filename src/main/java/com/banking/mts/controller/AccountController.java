package com.banking.mts.controller;

import com.banking.mts.constants.ResponseMessages;
import com.banking.mts.dto.*;
import com.banking.mts.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account API", description = "Account management endpoints")
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID", description = "Retrieve account information by account ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long id) {
        
        AccountResponse response = accountService.getAccountById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create new account", description = "Create a new bank account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Account created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Account already exists")
    })
    public ResponseEntity<AccountResponse> createAccount(
            @Parameter(description = "Account creation request", required = true)
            @Valid @RequestBody CreateAccountRequest request) {
        
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get account balance", description = "Retrieve current balance for specified account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long id) {
        
        BalanceResponse response = accountService.getBalanceById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get account transactions", description = "Get account statement with ledger entries")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statement retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "400", description = "Invalid page or size")
    })
    public ResponseEntity<AccountStatementResponse> getTransactions(
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Page number (default: 0)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (default: 20, max: 100)")
            @RequestParam(defaultValue = "20") int size) {
        
        AccountStatementResponse response = accountService.getTransactions(id, page, size);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deposit")
    @Operation(summary = "Deposit money", description = "Deposit money into an account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Deposit successful"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "422", description = "Account not active or invalid amount")
    })
    public ResponseEntity<AccountOperationResponse> deposit(
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Deposit request", required = true)
            @Valid @RequestBody DepositRequest request) {
        
        AccountOperationResponse response = accountService.deposit(id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw money", description = "Withdraw money from an account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Withdrawal successful"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "422", description = "Account not active, insufficient balance, or invalid amount")
    })
    public ResponseEntity<AccountOperationResponse> withdraw(
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Withdraw request", required = true)
            @Valid @RequestBody WithdrawRequest request) {
        
        AccountOperationResponse response = accountService.withdraw(id, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change account status", description = "Change account status to ACTIVE, FROZEN, or CLOSED")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status changed successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "422", description = "Invalid status or cannot close account with balance")
    })
    public ResponseEntity<AccountResponse> changeStatus(
            @Parameter(description = "Account ID", required = true)
            @PathVariable Long id,
            @Parameter(description = "Status change request", required = true)
            @Valid @RequestBody ChangeAccountStatusRequest request) {
        
        AccountResponse response = accountService.changeStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
