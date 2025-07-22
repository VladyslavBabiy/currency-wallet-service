package com.finance.wallet.controller;

import com.finance.wallet.dto.BalanceResponse;
import com.finance.wallet.dto.DepositRequest;
import com.finance.wallet.dto.ExchangeRequest;
import com.finance.wallet.dto.TransactionResponse;
import com.finance.wallet.dto.TransactionStatusResponse;
import com.finance.wallet.dto.WithdrawalRequest;
import com.finance.wallet.service.AccountService;
import com.finance.wallet.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transaction API", description = "APIs for wallet transactions")
public class TransactionController {
    
    private final TransactionService transactionService;
    private final AccountService accountService;
    
    @PostMapping("/deposit")
    @Operation(summary = "Create a deposit transaction")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody DepositRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        
        log.info("Deposit request for user: {} amount: {} {}", 
                request.getUserId(), request.getAmount(), request.getCurrency());
        
        TransactionResponse response = transactionService.createDepositTransaction(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    @PostMapping("/withdraw")
    @Operation(summary = "Create a withdrawal transaction")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody WithdrawalRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        
        log.info("Withdrawal request for user: {} amount: {} {}", 
                request.getUserId(), request.getAmount(), request.getCurrency());
        
        TransactionResponse response = transactionService.createWithdrawalTransaction(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    @PostMapping("/exchange")
    @Operation(summary = "Create a currency exchange transaction")
    public ResponseEntity<TransactionResponse> exchange(
            @Valid @RequestBody ExchangeRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        
        log.info("Exchange request for user: {} amount: {} {} to {}", 
                request.getUserId(), request.getAmount(), 
                request.getFromCurrency(), request.getToCurrency());
        
        TransactionResponse response = transactionService.createExchangeTransaction(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
    
    @GetMapping("/balance/{userId}")
    @Operation(summary = "Get user balances")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long userId) {
        
        log.info("Balance request for user: {}", userId);
        
        var balances = accountService.getBalancesByUserId(userId);
        BalanceResponse response = BalanceResponse.builder()
                .userId(userId)
                .balances(balances)
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/status/{userId}")
    @Operation(summary = "Get last transaction status for user")
    public ResponseEntity<TransactionStatusResponse> getStatus(@PathVariable Long userId) {
        
        log.info("Status request for user: {}", userId);
        
        Optional<TransactionResponse> lastTransaction = transactionService.getLastTransactionByUserId(userId);
        
        if (lastTransaction.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        TransactionStatusResponse response = TransactionStatusResponse.builder()
                .userId(userId)
                .lastTransactionStatus(lastTransaction.get().getStatus())
                .lastTransactionType(lastTransaction.get().getType().name())
                .errorMessage(lastTransaction.get().getErrorMessage())
                .lastUpdated(lastTransaction.get().getProcessedAt() != null ? 
                        lastTransaction.get().getProcessedAt() : lastTransaction.get().getCreatedAt())
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/history/{userId}")
    @Operation(summary = "Get transaction history for user")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(@PathVariable Long userId) {
        
        log.info("Transaction history request for user: {}", userId);
        
        List<TransactionResponse> transactions = transactionService.getTransactionsByUserId(userId);
        return ResponseEntity.ok(transactions);
    }
} 