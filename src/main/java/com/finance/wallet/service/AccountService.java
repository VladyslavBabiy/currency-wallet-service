package com.finance.wallet.service;

import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.User;
import com.finance.wallet.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {
    
    private final AccountRepository accountRepository;
    
    @Transactional(readOnly = true)
    public List<Account> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId);
    }
    
    @Transactional(readOnly = true)
    public Map<Account.Currency, BigDecimal> getBalancesByUserId(Long userId) {
        List<Account> accounts = accountRepository.findByUserId(userId);
        return accounts.stream()
                .collect(Collectors.toMap(
                        Account::getCurrency,
                        Account::getBalance,
                        (existing, replacement) -> existing
                ));
    }
    
    @Transactional
    public Account createAccount(User user, Account.Currency currency) {
        Optional<Account> existingAccount = accountRepository.findByUserAndCurrency(user, currency);
        
        if (existingAccount.isPresent()) {
            return existingAccount.get();
        }
        
        Account newAccount = Account.builder()
                .user(user)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .build();
        
        return accountRepository.save(newAccount);
    }
    
    @Transactional
    public Account updateBalance(Long userId, Account.Currency currency, BigDecimal amount) {
        Account account = accountRepository.findByUserIdAndCurrencyWithLock(userId, currency)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account not found for user " + userId + " and currency " + currency));
        
        BigDecimal newBalance = account.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Insufficient balance. Current: " + 
                    account.getBalance() + ", Requested: " + amount.abs());
        }
        
        account.setBalance(newBalance);
        Account savedAccount = accountRepository.save(account);
        
        log.info("Updated balance for user {} in currency {}: {} -> {}", 
                userId, currency, account.getBalance().subtract(amount), newBalance);
        
        return savedAccount;
    }
    
    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long userId, Account.Currency currency) {
        return accountRepository.findByUserIdAndCurrency(userId, currency)
                .map(Account::getBalance)
                .orElse(BigDecimal.ZERO);
    }
    
    @Transactional
    public boolean hasValidBalance(Long userId, Account.Currency currency, BigDecimal amount) {
        BigDecimal currentBalance = getBalance(userId, currency);
        return currentBalance.compareTo(amount) >= 0;
    }
} 