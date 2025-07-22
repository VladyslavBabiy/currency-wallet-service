package com.finance.wallet.service;

import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.Transaction;
import com.finance.wallet.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerServiceTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private AccountService accountService;

    @Mock
    private UserService userService;

    @Mock
    private FxRateService fxRateService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private KafkaConsumerService kafkaConsumerService;

    private User testUser;
    private Account usdAccount;
    private Account tryAccount;
    private KafkaProducerService.TransactionMessage depositMessage;
    private KafkaProducerService.TransactionMessage withdrawalMessage;
    private KafkaProducerService.TransactionMessage exchangeMessage;

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
                .build();

        tryAccount = Account.builder()
                .id(2L)
                .user(testUser)
                .currency(Account.Currency.TRY)
                .balance(new BigDecimal("33250.00"))
                .build();

        // Create test messages
        depositMessage = new KafkaProducerService.TransactionMessage();
        depositMessage.id = 1L;
        depositMessage.userId = 1L;
        depositMessage.type = "DEPOSIT";
        depositMessage.currency = "USD";
        depositMessage.amount = "100.00";
        depositMessage.status = "PENDING";
        depositMessage.description = "Test deposit";

        withdrawalMessage = new KafkaProducerService.TransactionMessage();
        withdrawalMessage.id = 2L;
        withdrawalMessage.userId = 1L;
        withdrawalMessage.type = "WITHDRAWAL";
        withdrawalMessage.currency = "USD";
        withdrawalMessage.amount = "50.00";
        withdrawalMessage.status = "PENDING";
        withdrawalMessage.description = "Test withdrawal";

        exchangeMessage = new KafkaProducerService.TransactionMessage();
        exchangeMessage.id = 3L;
        exchangeMessage.userId = 1L;
        exchangeMessage.type = "EXCHANGE";
        exchangeMessage.currency = "USD";
        exchangeMessage.amount = "100.00";
        exchangeMessage.status = "PENDING";
        exchangeMessage.description = "Exchange USD to TRY: Test exchange";
    }

    @Test
    void processTransaction_DepositType_ProcessesSuccessfully() {
        // Given
        when(userService.getUserById(1L)).thenReturn(testUser);

        // When
        kafkaConsumerService.processTransaction(depositMessage, "1", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(1L, Transaction.TransactionStatus.PROCESSING, null);
        verify(userService).getUserById(1L);
        verify(accountService).createAccount(testUser, Account.Currency.USD);
        verify(accountService).updateBalance(1L, Account.Currency.USD, new BigDecimal("100.00"));
        verify(transactionService).updateTransactionStatus(1L, Transaction.TransactionStatus.COMPLETED, null);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_WithdrawalType_ProcessesSuccessfully() {
        // Given
        when(accountService.hasValidBalance(1L, Account.Currency.USD, new BigDecimal("50.00"))).thenReturn(true);

        // When
        kafkaConsumerService.processTransaction(withdrawalMessage, "2", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(2L, Transaction.TransactionStatus.PROCESSING, null);
        verify(accountService).hasValidBalance(1L, Account.Currency.USD, new BigDecimal("50.00"));
        verify(accountService).updateBalance(1L, Account.Currency.USD, new BigDecimal("50.00").negate());
        verify(transactionService).updateTransactionStatus(2L, Transaction.TransactionStatus.COMPLETED, null);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_WithdrawalType_InsufficientBalance_FailsTransaction() {
        // Given
        when(accountService.hasValidBalance(1L, Account.Currency.USD, new BigDecimal("50.00"))).thenReturn(false);

        // When
        kafkaConsumerService.processTransaction(withdrawalMessage, "2", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(2L, Transaction.TransactionStatus.PROCESSING, null);
        verify(accountService).hasValidBalance(1L, Account.Currency.USD, new BigDecimal("50.00"));
        verify(accountService, never()).updateBalance(anyLong(), any(Account.Currency.class), any(BigDecimal.class));
        verify(transactionService).updateTransactionStatus(eq(2L), eq(Transaction.TransactionStatus.FAILED), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_ExchangeType_ProcessesSuccessfully() {
        // Given
        when(accountService.hasValidBalance(1L, Account.Currency.USD, new BigDecimal("100.00"))).thenReturn(true);
        when(fxRateService.getExchangeRate("USD", "TRY")).thenReturn(new BigDecimal("33.25"));
        when(userService.getUserById(1L)).thenReturn(testUser);

        // When
        kafkaConsumerService.processTransaction(exchangeMessage, "3", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(3L, Transaction.TransactionStatus.PROCESSING, null);
        verify(accountService).hasValidBalance(1L, Account.Currency.USD, new BigDecimal("100.00"));
        verify(fxRateService).getExchangeRate("USD", "TRY");
        verify(userService).getUserById(1L);
        verify(accountService).createAccount(testUser, Account.Currency.USD);
        verify(accountService).createAccount(testUser, Account.Currency.TRY);
        verify(accountService).updateBalance(1L, Account.Currency.USD, new BigDecimal("100.00").negate());
        verify(accountService).updateBalance(eq(1L), eq(Account.Currency.TRY), any(BigDecimal.class));
        verify(transactionService).updateTransactionStatus(3L, Transaction.TransactionStatus.COMPLETED, null);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_ExchangeType_InsufficientBalance_FailsTransaction() {
        // Given
        when(accountService.hasValidBalance(1L, Account.Currency.USD, new BigDecimal("100.00"))).thenReturn(false);

        // When
        kafkaConsumerService.processTransaction(exchangeMessage, "3", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(3L, Transaction.TransactionStatus.PROCESSING, null);
        verify(accountService).hasValidBalance(1L, Account.Currency.USD, new BigDecimal("100.00"));
        verify(fxRateService, never()).getExchangeRate(anyString(), anyString());
        verify(transactionService).updateTransactionStatus(eq(3L), eq(Transaction.TransactionStatus.FAILED), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_UnknownTransactionType_FailsTransaction() {
        // Given
        depositMessage.type = "UNKNOWN";

        // When
        kafkaConsumerService.processTransaction(depositMessage, "1", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(eq(1L), eq(Transaction.TransactionStatus.FAILED), anyString());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_ServiceThrowsException_FailsTransaction() {
        // Given
        when(userService.getUserById(1L)).thenThrow(new RuntimeException("Database error"));

        // When
        kafkaConsumerService.processTransaction(depositMessage, "1", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(eq(1L), eq(Transaction.TransactionStatus.FAILED), eq("Database error"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_InvalidCurrency_FailsTransaction() {
        // Given
        depositMessage.currency = "INVALID";

        // When
        kafkaConsumerService.processTransaction(depositMessage, "1", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(eq(1L), eq(Transaction.TransactionStatus.FAILED), contains("No enum constant"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void processTransaction_InvalidAmount_FailsTransaction() {
        // Given
        depositMessage.amount = "invalid_amount";

        // When
        kafkaConsumerService.processTransaction(depositMessage, "1", acknowledgment);

        // Then
        verify(transactionService).updateTransactionStatus(eq(1L), eq(Transaction.TransactionStatus.FAILED), anyString());
        verify(acknowledgment).acknowledge();
    }
} 