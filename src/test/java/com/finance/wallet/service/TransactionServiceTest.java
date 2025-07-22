package com.finance.wallet.service;

import com.finance.wallet.dto.DepositRequest;
import com.finance.wallet.dto.ExchangeRequest;
import com.finance.wallet.dto.TransactionResponse;
import com.finance.wallet.dto.WithdrawalRequest;
import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.Transaction;
import com.finance.wallet.entity.User;
import com.finance.wallet.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserService userService;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private TransactionService transactionService;

    private User testUser;
    private Transaction testTransaction;
    private DepositRequest depositRequest;
    private WithdrawalRequest withdrawalRequest;
    private ExchangeRequest exchangeRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("John Doe")
                .email("john.doe@example.com")
                .password("$2a$10$encodedPassword123")
                .build();

        testTransaction = Transaction.builder()
                .id(1L)
                .user(testUser)
                .type(Transaction.TransactionType.DEPOSIT)
                .currency(Account.Currency.USD)
                .amount(new BigDecimal("100.00"))
                .status(Transaction.TransactionStatus.PENDING)
                .description("Test deposit")
                .idempotencyKey("test-key-123")
                .externalReference("TXN-12345678")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        depositRequest = new DepositRequest(
                1L,
                Account.Currency.USD,
                new BigDecimal("100.00"),
                "Test deposit"
        );

        withdrawalRequest = new WithdrawalRequest(
                1L,
                Account.Currency.USD,
                new BigDecimal("50.00"),
                "Test withdrawal"
        );

        exchangeRequest = new ExchangeRequest(
                1L,
                Account.Currency.USD,
                Account.Currency.TRY,
                new BigDecimal("100.00"),
                "Test exchange"
        );
    }

    @Test
    void createDepositTransaction_WithoutIdempotencyKey_CreatesNewTransaction() {
        when(userService.getUserById(1L)).thenReturn(testUser);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        TransactionResponse result = transactionService.createDepositTransaction(depositRequest, null);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getType()).isEqualTo(Transaction.TransactionType.DEPOSIT);
        assertThat(result.getStatus()).isEqualTo(Transaction.TransactionStatus.PENDING);

        verify(userService).getUserById(1L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaProducerService).sendTransactionMessage(any(Transaction.class));
        verify(transactionRepository, never()).findByIdempotencyKey(anyString());
    }

    @Test
    void createDepositTransaction_WithIdempotencyKey_ReturnsExistingTransaction() {
        String idempotencyKey = "test-key-123";
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.of(testTransaction));

        TransactionResponse result = transactionService.createDepositTransaction(depositRequest, idempotencyKey);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);

        verify(transactionRepository).findByIdempotencyKey(idempotencyKey);
        verify(userService, never()).getUserById(any());
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(kafkaProducerService, never()).sendTransactionMessage(any(Transaction.class));
    }

    @Test
    void createDepositTransaction_WithNewIdempotencyKey_CreatesNewTransaction() {
        String idempotencyKey = "new-key-456";
        when(transactionRepository.findByIdempotencyKey(idempotencyKey))
                .thenReturn(Optional.empty());
        when(userService.getUserById(1L)).thenReturn(testUser);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        TransactionResponse result = transactionService.createDepositTransaction(depositRequest, idempotencyKey);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);

        verify(transactionRepository).findByIdempotencyKey(idempotencyKey);
        verify(userService).getUserById(1L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaProducerService).sendTransactionMessage(any(Transaction.class));
    }

    @Test
    void createWithdrawalTransaction_CreatesTransactionSuccessfully() {
        Transaction withdrawalTransaction = testTransaction.toBuilder()
                .type(Transaction.TransactionType.WITHDRAWAL)
                .build();
        
        when(userService.getUserById(1L)).thenReturn(testUser);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(withdrawalTransaction);

        TransactionResponse result = transactionService.createWithdrawalTransaction(withdrawalRequest, null);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(Transaction.TransactionType.WITHDRAWAL);

        verify(userService).getUserById(1L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaProducerService).sendTransactionMessage(any(Transaction.class));
    }

    @Test
    void createExchangeTransaction_CreatesTransactionSuccessfully() {
        Transaction exchangeTransaction = testTransaction.toBuilder()
                .type(Transaction.TransactionType.EXCHANGE)
                .description("Exchange USD to TRY: Test exchange")
                .build();
        
        when(userService.getUserById(1L)).thenReturn(testUser);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(exchangeTransaction);

        TransactionResponse result = transactionService.createExchangeTransaction(exchangeRequest, null);

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo(Transaction.TransactionType.EXCHANGE);

        verify(userService).getUserById(1L);
        verify(transactionRepository).save(any(Transaction.class));
        verify(kafkaProducerService).sendTransactionMessage(any(Transaction.class));
    }

    @Test
    void getTransactionsByUserId_ReturnsUserTransactions() {
        List<Transaction> transactions = Collections.singletonList(testTransaction);
        when(transactionRepository.findByUserId(1L)).thenReturn(transactions);

        List<TransactionResponse> result = transactionService.getTransactionsByUserId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(1L);

        verify(transactionRepository).findByUserId(1L);
    }

    @Test
    void getLastTransactionByUserId_ReturnsLastTransaction() {
        when(transactionRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(testTransaction));

        Optional<TransactionResponse> result = transactionService.getLastTransactionByUserId(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(1L);

        verify(transactionRepository).findTopByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void getLastTransactionByUserId_WhenNoTransactions_ReturnsEmpty() {
        when(transactionRepository.findTopByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.empty());

        Optional<TransactionResponse> result = transactionService.getLastTransactionByUserId(1L);

        assertThat(result).isEmpty();

        verify(transactionRepository).findTopByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void updateTransactionStatus_UpdatesStatusSuccessfully() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        Transaction result = transactionService.updateTransactionStatus(1L,
                Transaction.TransactionStatus.COMPLETED, null);

        assertThat(result.getStatus()).isEqualTo(Transaction.TransactionStatus.COMPLETED);
        assertThat(result.getProcessedAt()).isNotNull();

        verify(transactionRepository).findById(1L);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void updateTransactionStatus_WithErrorMessage_UpdatesStatusAndError() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(testTransaction);

        Transaction result = transactionService.updateTransactionStatus(1L,
                Transaction.TransactionStatus.FAILED, "Payment failed");

        assertThat(result.getStatus()).isEqualTo(Transaction.TransactionStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("Payment failed");
        assertThat(result.getProcessedAt()).isNotNull();

        verify(transactionRepository).findById(1L);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void updateTransactionStatus_WhenTransactionNotFound_ThrowsException() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.updateTransactionStatus(99L,
                Transaction.TransactionStatus.COMPLETED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Transaction not found: 99");

        verify(transactionRepository).findById(99L);
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void createDepositTransaction_WhenUserNotFound_ThrowsException() {
        when(userService.getUserById(1L)).thenThrow(new IllegalArgumentException("User not found"));

        assertThatThrownBy(() -> transactionService.createDepositTransaction(depositRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");

        verify(userService).getUserById(1L);
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(kafkaProducerService, never()).sendTransactionMessage(any(Transaction.class));
    }
} 