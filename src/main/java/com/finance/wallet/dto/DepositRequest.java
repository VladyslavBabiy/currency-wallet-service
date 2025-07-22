package com.finance.wallet.dto;

import com.finance.wallet.entity.Account;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositRequest {
    
    @NotNull(message = "User ID is required")
    private Long userId;
    
    @NotNull(message = "Currency is required")
    private Account.Currency currency;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.000001", message = "Amount must be greater than 0")
    private BigDecimal amount;
    
    private String description;
} 