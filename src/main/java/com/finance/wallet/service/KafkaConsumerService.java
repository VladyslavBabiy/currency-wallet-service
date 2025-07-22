package com.finance.wallet.service;

import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {
    
    private final TransactionService transactionService;
    private final AccountService accountService;
    private final UserService userService;
    private final FxRateService fxRateService;
    
    @KafkaListener(topics = "${wallet.kafka.topics.transactions}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void processTransaction(@Payload KafkaProducerService.TransactionMessage message,
                                  @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                  Acknowledgment acknowledgment) {
        
        log.info("Processing transaction message with ID: {}", message.id);
        
        try {
            Transaction.TransactionType type = Transaction.TransactionType.valueOf(message.type);
            Account.Currency currency = Account.Currency.valueOf(message.currency);
            BigDecimal amount = new BigDecimal(message.amount);
            
            switch (type) {
                case DEPOSIT:
                    processDeposit(message.userId, currency, amount, message.id);
                    break;
                case WITHDRAWAL:
                    processWithdrawal(message.userId, currency, amount, message.id);
                    break;
                case EXCHANGE:
                    processExchange(message, message.id);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown transaction type: " + type);
            }
            
            transactionService.updateTransactionStatus(message.id,
                    Transaction.TransactionStatus.COMPLETED, null);
            
            acknowledgment.acknowledge();
            log.info("Successfully processed transaction: {}", message.id);
            
        } catch (Exception e) {
            log.error("Failed to process transaction: {}", message.id, e);
            
            transactionService.updateTransactionStatus(message.id,
                    Transaction.TransactionStatus.FAILED, e.getMessage());
            
            acknowledgment.acknowledge();
        }
    }
    
    private void processDeposit(Long userId, Account.Currency currency, BigDecimal amount, Long transactionId) {
        log.info("Processing deposit: {} {} for user {}", amount, currency, userId);
        
        transactionService.updateTransactionStatus(transactionId,
                Transaction.TransactionStatus.PROCESSING, null);
        
        var user = userService.getUserById(userId);
        accountService.createAccount(user, currency);
        accountService.updateBalance(userId, currency, amount);
        
        log.info("Completed deposit: {} {} for user {}", amount, currency, userId);
    }
    
    private void processWithdrawal(Long userId, Account.Currency currency, BigDecimal amount, Long transactionId) {
        log.info("Processing withdrawal: {} {} for user {}", amount, currency, userId);
        
        transactionService.updateTransactionStatus(transactionId,
                Transaction.TransactionStatus.PROCESSING, null);
        
        if (!accountService.hasValidBalance(userId, currency, amount)) {
            throw new IllegalArgumentException("Insufficient balance for withdrawal");
        }
        
        accountService.updateBalance(userId, currency, amount.negate());
        
        log.info("Completed withdrawal: {} {} for user {}", amount, currency, userId);
    }
    
    private void processExchange(KafkaProducerService.TransactionMessage message, Long transactionId) {
        String description = message.description;
        if (description == null || !description.contains("Exchange")) {
            throw new IllegalArgumentException("Invalid exchange transaction description");
        }
        
        String[] parts = description.split(" ");
        if (parts.length < 4) {
            throw new IllegalArgumentException("Invalid exchange transaction format");
        }
        
        Account.Currency fromCurrency = Account.Currency.valueOf(parts[1]);
        Account.Currency toCurrency = Account.Currency.valueOf(parts[3].replace(":", ""));
        BigDecimal fromAmount = new BigDecimal(message.amount);
        
        log.info("Processing exchange: {} {} to {} for user {}", 
                fromAmount, fromCurrency, toCurrency, message.userId);
        
        transactionService.updateTransactionStatus(transactionId,
                Transaction.TransactionStatus.PROCESSING, null);
        
        if (!accountService.hasValidBalance(message.userId, fromCurrency, fromAmount)) {
            throw new IllegalArgumentException("Insufficient balance for exchange");
        }
        
        BigDecimal exchangeRate = fxRateService.getExchangeRate(fromCurrency.name(), toCurrency.name());
        BigDecimal toAmount = fromAmount.multiply(exchangeRate);
        
        var user = userService.getUserById(message.userId);
        accountService.createAccount(user, fromCurrency);
        accountService.createAccount(user, toCurrency);
        
        accountService.updateBalance(message.userId, fromCurrency, fromAmount.negate());
        accountService.updateBalance(message.userId, toCurrency, toAmount);
        
        log.info("Completed exchange: {} {} to {} {} for user {} (rate: {})", 
                fromAmount, fromCurrency, toAmount, toCurrency, message.userId, exchangeRate);
    }
} 