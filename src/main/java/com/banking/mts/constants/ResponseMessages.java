package com.banking.mts.constants;

public final class ResponseMessages {
    
    private ResponseMessages() {
        // Utility class
    }
    
    public static final String ACCOUNT_FOUND_200 = "Account found";
    public static final String ACCOUNT_FOUND_404 = "Account not found";
    
    public static final String ACCOUNT_CREATED_201 = "Account created successfully";
    public static final String ACCOUNT_CREATED_400 = "Invalid request data";
    public static final String ACCOUNT_CREATED_409 = "Account already exists";
    
    public static final String BALANCE_RETRIEVED_200 = "Balance retrieved successfully";
    public static final String BALANCE_RETRIEVED_404 = "Account not found";
    
    public static final String TRANSFER_CREATED_201 = "Transfer created successfully";
    public static final String TRANSFER_CREATED_400 = "Invalid request data";
    public static final String TRANSFER_CREATED_404 = "Account not found";
    public static final String TRANSFER_CREATED_409 = "Insufficient balance";
    
    public static final String TRANSFER_FOUND_200 = "Transfer found";
    public static final String TRANSFER_FOUND_404 = "Transfer not found";
}
