package com.banking.mts.controller;

import com.banking.mts.dto.CreateTransferRequest;
import com.banking.mts.dto.TransferResponse;
import com.banking.mts.dto.TransferResult;
import com.banking.mts.service.TransferService;
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
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfer API", description = "Money transfer endpoints")
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    @Operation(summary = "Create new transfer", description = "Create a new money transfer between accounts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Transfer created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data or missing Idempotency-Key"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "409", description = "Insufficient balance or idempotency conflict")
    })
    public ResponseEntity<TransferResponse> createTransfer(
            @Parameter(description = "Transfer creation request", required = true)
            @Valid @RequestBody CreateTransferRequest request,
            @Parameter(description = "Idempotency key", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        TransferResult result = transferService.createTransfer(request, idempotencyKey);
        HttpStatus status = result.isReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result.getResponse());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transfer by ID", description = "Retrieve transfer information by transfer ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Transfer found"),
        @ApiResponse(responseCode = "404", description = "Transfer not found")
    })
    public ResponseEntity<TransferResponse> getTransfer(
            @Parameter(description = "Transfer ID", required = true)
            @PathVariable Long id) {
        
        TransferResponse response = transferService.getTransferById(id);
        return ResponseEntity.ok(response);
    }

}
