package com.finance.wallet.service;

import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.User;
import com.finance.wallet.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    private User testUser;
    private Account usdAccount;
    private Account tryAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .password("$2a$10$encodedPassword123")
                .build();

        usdAccount = Account.builder()
                .id(1L)
                .user(testUser)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("1000.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        tryAccount = Account.builder()
                .id(2L)
                .user(testUser)
                .currency(Account.Currency.TRY)
                .balance(new BigDecimal("33250.00"))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAccountsByUserId_ReturnsUserAccounts() {
        List<Account> accounts = Arrays.asList(usdAccount, tryAccount);
        when(accountRepository.findByUserId(1L)).thenReturn(accounts);
        List<Account> result = accountService.getAccountsByUserId(1L);
        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyInAnyOrder(usdAccount, tryAccount);
        verify(accountRepository).findByUserId(1L);
    }

    @Test
    void getBalancesByUserId_ReturnsBalancesMap() {
        List<Account> accounts = Arrays.asList(usdAccount, tryAccount);
        when(accountRepository.findByUserId(1L)).thenReturn(accounts);

        Map<Account.Currency, BigDecimal> result = accountService.getBalancesByUserId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(Account.Currency.USD)).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.get(Account.Currency.TRY)).isEqualByComparingTo(new BigDecimal("33250.00"));
        verify(accountRepository).findByUserId(1L);
    }

    @Test
    void createAccount_WhenAccountExists_ReturnsExistingAccount() {
        when(accountRepository.findByUserAndCurrency(testUser, Account.Currency.USD))
                .thenReturn(Optional.of(usdAccount));

        accountService.createAccount(testUser, Account.Currency.USD);

        verify(accountRepository).findByUserAndCurrency(testUser, Account.Currency.USD);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void createAccount_WhenAccountDoesNotExist_CreatesNewAccount() {
        when(accountRepository.findByUserAndCurrency(testUser, Account.Currency.USD))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any(Account.class))).thenReturn(usdAccount);

        accountService.createAccount(testUser, Account.Currency.USD);

        verify(accountRepository).findByUserAndCurrency(testUser, Account.Currency.USD);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void updateBalance_WithSufficientFunds_UpdatesBalanceSuccessfully() {
        Account accountWithBalance = Account.builder()
                .id(1L)
                .user(testUser)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("1000.00"))
                .build();

        when(accountRepository.findByUserIdAndCurrencyWithLock(1L, Account.Currency.USD))
                .thenReturn(Optional.of(accountWithBalance));
        when(accountRepository.save(any(Account.class))).thenReturn(accountWithBalance);

        Account result = accountService.updateBalance(1L, Account.Currency.USD, new BigDecimal("100.00"));

        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));

        verify(accountRepository).findByUserIdAndCurrencyWithLock(1L, Account.Currency.USD);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void updateBalance_WithInsufficientFunds_ThrowsException() {
        Account accountWithLowBalance = Account.builder()
                .id(1L)
                .user(testUser)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("50.00"))
                .build();

        when(accountRepository.findByUserIdAndCurrencyWithLock(1L, Account.Currency.USD))
                .thenReturn(Optional.of(accountWithLowBalance));

        assertThatThrownBy(() -> accountService.updateBalance(1L, Account.Currency.USD, new BigDecimal("-100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");

        verify(accountRepository).findByUserIdAndCurrencyWithLock(1L, Account.Currency.USD);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void updateBalance_WhenAccountNotFound_ThrowsException() {
        when(accountRepository.findByUserIdAndCurrencyWithLock(1L, Account.Currency.USD))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.updateBalance(1L, Account.Currency.USD, new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");

        verify(accountRepository).findByUserIdAndCurrencyWithLock(1L, Account.Currency.USD);
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void getBalance_WhenAccountExists_ReturnsBalance() {
        when(accountRepository.findByUserIdAndCurrency(1L, Account.Currency.USD))
                .thenReturn(Optional.of(usdAccount));

        BigDecimal result = accountService.getBalance(1L, Account.Currency.USD);

        assertThat(result).isEqualByComparingTo(new BigDecimal("1000.00"));

        verify(accountRepository).findByUserIdAndCurrency(1L, Account.Currency.USD);
    }

    @Test
    void getBalance_WhenAccountDoesNotExist_ReturnsZero() {
        when(accountRepository.findByUserIdAndCurrency(1L, Account.Currency.USD))
                .thenReturn(Optional.empty());

        BigDecimal result = accountService.getBalance(1L, Account.Currency.USD);
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        verify(accountRepository).findByUserIdAndCurrency(1L, Account.Currency.USD);
    }

    @Test
    void hasValidBalance_WithSufficientFunds_ReturnsTrue() {
        when(accountRepository.findByUserIdAndCurrency(1L, Account.Currency.USD))
                .thenReturn(Optional.of(usdAccount));

        boolean result = accountService.hasValidBalance(1L, Account.Currency.USD, new BigDecimal("500.00"));
        assertThat(result).isTrue();
        verify(accountRepository).findByUserIdAndCurrency(1L, Account.Currency.USD);
    }

    @Test
    void hasValidBalance_WithInsufficientFunds_ReturnsFalse() {
        when(accountRepository.findByUserIdAndCurrency(1L, Account.Currency.USD))
                .thenReturn(Optional.of(usdAccount));

        boolean result = accountService.hasValidBalance(1L, Account.Currency.USD, new BigDecimal("1500.00"));
        assertThat(result).isFalse();
        verify(accountRepository).findByUserIdAndCurrency(1L, Account.Currency.USD);
    }

    @Test
    void hasValidBalance_WhenAccountDoesNotExist_ReturnsFalse() {
        when(accountRepository.findByUserIdAndCurrency(1L, Account.Currency.USD))
                .thenReturn(Optional.empty());

        boolean result = accountService.hasValidBalance(1L, Account.Currency.USD, new BigDecimal("100.00"));
        assertThat(result).isFalse();
        verify(accountRepository).findByUserIdAndCurrency(1L, Account.Currency.USD);
    }
} 