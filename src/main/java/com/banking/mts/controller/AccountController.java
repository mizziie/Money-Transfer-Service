package com.banking.mts.controller;

import com.banking.mts.constants.ResponseMessages;
import com.banking.mts.dto.AccountResponse;
import com.banking.mts.dto.BalanceResponse;
import com.banking.mts.dto.CreateAccountRequest;
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

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get account by account number", description = "Retrieve account information using account number")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Account found"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Account number", required = true)
            @PathVariable String accountNumber) {
        
        AccountResponse response = accountService.getAccountByNumber(accountNumber);
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

    @GetMapping("/{accountNumber}/balance")
    @Operation(summary = "Get account balance", description = "Retrieve current balance for specified account")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Balance retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account number", required = true)
            @PathVariable String accountNumber) {
        
        BalanceResponse response = accountService.getBalance(accountNumber);
        return ResponseEntity.ok(response);
    }
}
