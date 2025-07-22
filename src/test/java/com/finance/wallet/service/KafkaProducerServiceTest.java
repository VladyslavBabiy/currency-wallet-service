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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private CompletableFuture<SendResult<String, Object>> future;

    @InjectMocks
    private KafkaProducerService kafkaProducerService;

    private Transaction testTransaction;
    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(kafkaProducerService, "transactionTopic", "test.wallet.txn");

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
    }

    @Test
    void sendTransactionMessage_SendsMessageSuccessfully() {
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        kafkaProducerService.sendTransactionMessage(testTransaction);

        verify(kafkaTemplate).send(eq("test.wallet.txn"), eq("1"), any(KafkaProducerService.TransactionMessage.class));
        verify(future).whenComplete(any());
    }

    @Test
    void sendTransactionMessage_WithNullTransaction_HandlesGracefully() {
        assertThatCode(() -> kafkaProducerService.sendTransactionMessage(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void transactionMessage_fromEntity_CreatesCorrectMessage() {
        KafkaProducerService.TransactionMessage message =
                KafkaProducerService.TransactionMessage.fromEntity(testTransaction);

        assert message.id.equals(1L);
        assert message.userId.equals(1L);
        assert message.type.equals("DEPOSIT");
        assert message.currency.equals("USD");
        assert message.amount.equals("100.00");
        assert message.status.equals("PENDING");
        assert message.description.equals("Test deposit");
        assert message.idempotencyKey.equals("test-key-123");
        assert message.externalReference.equals("TXN-12345678");
    }

    @Test
    void sendTransactionMessage_WithKafkaTemplateException_HandlesGracefully() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka error"));

        kafkaProducerService.sendTransactionMessage(testTransaction);

        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void sendTransactionMessage_WithDifferentTransactionTypes_SendsCorrectMessage() {
        Transaction withdrawalTransaction = testTransaction.toBuilder()
                .type(Transaction.TransactionType.WITHDRAWAL)
                .build();

        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        kafkaProducerService.sendTransactionMessage(withdrawalTransaction);

        verify(kafkaTemplate).send(eq("test.wallet.txn"), eq("1"), any(KafkaProducerService.TransactionMessage.class));

        Transaction exchangeTransaction = testTransaction.toBuilder()
                .type(Transaction.TransactionType.EXCHANGE)
                .build();

        kafkaProducerService.sendTransactionMessage(exchangeTransaction);

        verify(kafkaTemplate, times(2)).send(eq("test.wallet.txn"), eq("1"), any(KafkaProducerService.TransactionMessage.class));
    }
} 