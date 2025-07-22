package com.finance.wallet.dto;

import com.finance.wallet.entity.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {
    
    private Long userId;
    private Map<Account.Currency, BigDecimal> balances;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CurrencyBalance {
        private Account.Currency currency;
        private BigDecimal balance;
    }
} 