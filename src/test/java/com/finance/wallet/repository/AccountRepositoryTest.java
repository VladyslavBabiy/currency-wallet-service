package com.finance.wallet.repository;

import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AccountRepository accountRepository;

    private User testUser;
    private Account usdAccount;
    private Account tryAccount;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .name("John Doe")
                .email("john.doe@example.com")
                .password("$2a$10$encodedPassword123")
                .build();

        usdAccount = Account.builder()
                .user(testUser)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("1000.00"))
                .build();

        tryAccount = Account.builder()
                .user(testUser)
                .currency(Account.Currency.TRY)
                .balance(new BigDecimal("33250.00"))
                .build();
    }

    @Test
    void findByUserId_ReturnsUserAccounts() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);
        tryAccount.setUser(savedUser);

        entityManager.persistAndFlush(usdAccount);
        entityManager.persistAndFlush(tryAccount);
        entityManager.clear();

        List<Account> accounts = accountRepository.findByUserId(savedUser.getId());

        assertThat(accounts).hasSize(2);
        assertThat(accounts).extracting(Account::getCurrency)
                .containsExactlyInAnyOrder(Account.Currency.USD, Account.Currency.TRY);
        assertThat(accounts).anySatisfy(account ->
                        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00")))
                .anySatisfy(account ->
                        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("33250.00")));
    }

    @Test
    void findByUserId_NoAccounts_ReturnsEmptyList() {
        User savedUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        List<Account> accounts = accountRepository.findByUserId(savedUser.getId());

        assertThat(accounts).isEmpty();
    }

    @Test
    void findByUserAndCurrency_ExistingAccount_ReturnsAccount() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);
        Account savedAccount = entityManager.persistAndFlush(usdAccount);
        entityManager.clear();

        Optional<Account> found = accountRepository.findByUserAndCurrency(savedUser, Account.Currency.USD);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedAccount.getId());
        assertThat(found.get().getCurrency()).isEqualTo(Account.Currency.USD);
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void findByUserAndCurrency_NonExistingAccount_ReturnsEmpty() {
        User savedUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        Optional<Account> found = accountRepository.findByUserAndCurrency(savedUser, Account.Currency.USD);

        assertThat(found).isEmpty();
    }

    @Test
    void findByUserIdAndCurrency_ExistingAccount_ReturnsAccount() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);
        Account savedAccount = entityManager.persistAndFlush(usdAccount);
        entityManager.clear();

        Optional<Account> found = accountRepository.findByUserIdAndCurrency(savedUser.getId(), Account.Currency.USD);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedAccount.getId());
        assertThat(found.get().getCurrency()).isEqualTo(Account.Currency.USD);
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void findByUserIdAndCurrency_NonExistingAccount_ReturnsEmpty() {
        User savedUser = entityManager.persistAndFlush(testUser);
        entityManager.clear();

        Optional<Account> found = accountRepository.findByUserIdAndCurrency(savedUser.getId(), Account.Currency.TRY);

        assertThat(found).isEmpty();
    }

    @Test
    void findByUserIdAndCurrencyWithLock_ExistingAccount_ReturnsAccount() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);
        Account savedAccount = entityManager.persistAndFlush(usdAccount);
        entityManager.clear();

        Optional<Account> found = accountRepository.findByUserIdAndCurrencyWithLock(savedUser.getId(), Account.Currency.USD);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedAccount.getId());
        assertThat(found.get().getCurrency()).isEqualTo(Account.Currency.USD);
        assertThat(found.get().getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    void save_NewAccount_PersistsWithGeneratedId() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);

        Account savedAccount = accountRepository.save(usdAccount);

        assertThat(savedAccount.getId()).isNotNull();
        assertThat(savedAccount.getUser().getId()).isEqualTo(savedUser.getId());
        assertThat(savedAccount.getCurrency()).isEqualTo(Account.Currency.USD);
        assertThat(savedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(savedAccount.getCreatedAt()).isNotNull();
        assertThat(savedAccount.getUpdatedAt()).isNotNull();
    }

    @Test
    void save_DuplicateUserCurrency_ThrowsException() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);
        entityManager.persistAndFlush(usdAccount);
        entityManager.clear();

        Account duplicateAccount = Account.builder()
                .user(savedUser)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("500.00"))
                .build();

        assertThatThrownBy(() -> {
            accountRepository.save(duplicateAccount);
            entityManager.flush();
        }).hasMessageContaining("could not execute statement");
    }

    @Test
    void updateBalance_ModifiesAccountBalance() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);
        Account savedAccount = entityManager.persistAndFlush(usdAccount);
        entityManager.clear();

        savedAccount.setBalance(new BigDecimal("1500.00"));
        Account updatedAccount = accountRepository.save(savedAccount);
        entityManager.flush();

        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(updatedAccount.getUpdatedAt()).isAfter(updatedAccount.getCreatedAt());
    }

    @Test
    void findByUserId_MultipleUsers_ReturnsOnlyUserAccounts() {
        User user1 = entityManager.persistAndFlush(testUser);
        User user2 = User.builder()
                .name("Jane Doe")
                .email("jane.doe@example.com")
                .password("$2a$10$encodedPassword123")
                .build();
        User savedUser2 = entityManager.persistAndFlush(user2);

        usdAccount.setUser(user1);
        tryAccount.setUser(user1);

        Account user2Account = Account.builder()
                .user(savedUser2)
                .currency(Account.Currency.USD)
                .balance(new BigDecimal("500.00"))
                .build();

        entityManager.persistAndFlush(usdAccount);
        entityManager.persistAndFlush(tryAccount);
        entityManager.persistAndFlush(user2Account);
        entityManager.clear();

        List<Account> user1Accounts = accountRepository.findByUserId(user1.getId());
        List<Account> user2Accounts = accountRepository.findByUserId(savedUser2.getId());

        assertThat(user1Accounts).hasSize(2);
        assertThat(user2Accounts).hasSize(1);

        assertThat(user1Accounts).extracting(Account::getCurrency)
                .containsExactlyInAnyOrder(Account.Currency.USD, Account.Currency.TRY);
        assertThat(user2Accounts).extracting(Account::getCurrency)
                .containsExactly(Account.Currency.USD);
    }

    @Test
    void delete_RemovesAccount() {
        User savedUser = entityManager.persistAndFlush(testUser);
        usdAccount.setUser(savedUser);
        Account savedAccount = entityManager.persistAndFlush(usdAccount);
        entityManager.clear();

        accountRepository.deleteById(savedAccount.getId());
        entityManager.flush();

        Optional<Account> found = accountRepository.findById(savedAccount.getId());
        assertThat(found).isEmpty();
    }
} 