package com.finance.wallet.dto;

import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public class TransactionResponse {
    
    private Long id;
    private Long userId;
    private Transaction.TransactionType type;
    private Account.Currency currency;
    private BigDecimal amount;
    private Transaction.TransactionStatus status;
    private String description;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    
    public static TransactionResponse fromEntity(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUser().getId())
                .type(transaction.getType())
                .currency(transaction.getCurrency())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .errorMessage(transaction.getErrorMessage())
                .createdAt(transaction.getCreatedAt())
                .processedAt(transaction.getProcessedAt())
                .build();
    }
} 