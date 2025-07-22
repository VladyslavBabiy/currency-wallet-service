package com.finance.wallet.service;

import com.finance.wallet.dto.DepositRequest;
import com.finance.wallet.dto.ExchangeRequest;
import com.finance.wallet.dto.TransactionResponse;
import com.finance.wallet.dto.WithdrawalRequest;
import com.finance.wallet.entity.Transaction;
import com.finance.wallet.entity.User;
import com.finance.wallet.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final KafkaProducerService kafkaProducerService;
    
    @Transactional
    public TransactionResponse createDepositTransaction(DepositRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing transaction for idempotency key: {}", idempotencyKey);
                return TransactionResponse.fromEntity(existing.get());
            }
        }
        
        User user = userService.getUserById(request.getUserId());
        
        Transaction transaction = Transaction.builder()
                .user(user)
                .type(Transaction.TransactionType.DEPOSIT)
                .currency(request.getCurrency())
                .amount(request.getAmount())
                .status(Transaction.TransactionStatus.PENDING)
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .externalReference(generateReference())
                .build();
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Created deposit transaction: {} for user: {}", savedTransaction.getId(), user.getId());
        
        kafkaProducerService.sendTransactionMessage(savedTransaction);
        
        return TransactionResponse.fromEntity(savedTransaction);
    }
    
    @Transactional
    public TransactionResponse createWithdrawalTransaction(WithdrawalRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                getLog().info("Returning existing transaction for idempotency key: {}", idempotencyKey);
                return TransactionResponse.fromEntity(existing.get());
            }
        }
        
        User user = userService.getUserById(request.getUserId());
        
        Transaction transaction = Transaction.builder()
                .user(user)
                .type(Transaction.TransactionType.WITHDRAWAL)
                .currency(request.getCurrency())
                .amount(request.getAmount())
                .status(Transaction.TransactionStatus.PENDING)
                .description(request.getDescription())
                .idempotencyKey(idempotencyKey)
                .externalReference(generateReference())
                .build();
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Created withdrawal transaction: {} for user: {}", savedTransaction.getId(), user.getId());
        
        kafkaProducerService.sendTransactionMessage(savedTransaction);
        
        return TransactionResponse.fromEntity(savedTransaction);
    }

    private static Logger getLog() {
        return log;
    }

    @Transactional
    public TransactionResponse createExchangeTransaction(ExchangeRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing transaction for idempotency key: {}", idempotencyKey);
                return TransactionResponse.fromEntity(existing.get());
            }
        }
        
        User user = userService.getUserById(request.getUserId());
        
        Transaction transaction = Transaction.builder()
                .user(user)
                .type(Transaction.TransactionType.EXCHANGE)
                .currency(request.getFromCurrency())
                .amount(request.getAmount())
                .status(Transaction.TransactionStatus.PENDING)
                .description(String.format("Exchange %s to %s: %s", 
                        request.getFromCurrency(), request.getToCurrency(), 
                        request.getDescription() != null ? request.getDescription() : ""))
                .idempotencyKey(idempotencyKey)
                .externalReference(generateReference())
                .build();
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Created exchange transaction: {} for user: {}", savedTransaction.getId(), user.getId());
        
        kafkaProducerService.sendTransactionMessage(savedTransaction);
        
        return TransactionResponse.fromEntity(savedTransaction);
    }
    
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByUserId(Long userId) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        return transactions.stream()
                .map(TransactionResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Optional<TransactionResponse> getLastTransactionByUserId(Long userId) {
        return transactionRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(TransactionResponse::fromEntity);
    }
    
    @Transactional
    public Transaction updateTransactionStatus(Long transactionId, Transaction.TransactionStatus status, String errorMessage) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        transaction.setStatus(status);
        transaction.setErrorMessage(errorMessage);
        if (status == Transaction.TransactionStatus.COMPLETED || status == Transaction.TransactionStatus.FAILED) {
            transaction.setProcessedAt(LocalDateTime.now());
        }
        
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Updated transaction {} status to {}", transactionId, status);
        
        return savedTransaction;
    }
    
    private String generateReference() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
} 