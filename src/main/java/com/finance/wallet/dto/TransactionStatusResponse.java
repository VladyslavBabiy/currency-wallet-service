package com.finance.wallet.dto;

import com.finance.wallet.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionStatusResponse {
    
    private Long userId;
    private Transaction.TransactionStatus lastTransactionStatus;
    private String lastTransactionType;
    private String errorMessage;
    private LocalDateTime lastUpdated;
    
    public static TransactionStatusResponse fromTransaction(Transaction transaction) {
        return TransactionStatusResponse.builder()
                .userId(transaction.getUser().getId())
                .lastTransactionStatus(transaction.getStatus())
                .lastTransactionType(transaction.getType().name())
                .errorMessage(transaction.getErrorMessage())
                .lastUpdated(transaction.getUpdatedAt())
                .build();
    }
} 