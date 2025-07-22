package com.finance.wallet.service;

import com.finance.wallet.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${wallet.kafka.topics.transactions}")
    private String transactionTopic;
    
    public void sendTransactionMessage(Transaction transaction) {
        try {
            TransactionMessage message = TransactionMessage.fromEntity(transaction);
            
            CompletableFuture<SendResult<String, Object>> future = 
                    kafkaTemplate.send(transactionTopic, transaction.getId().toString(), message);
            
            future.whenComplete((result, exception) -> {
                if (exception == null) {
                    log.info("Successfully sent transaction message for ID: {} to topic: {}", 
                            transaction.getId(), transactionTopic);
                } else {
                    log.error("Failed to send transaction message for ID: {} to topic: {}", 
                            transaction.getId(), transactionTopic, exception);
                }
            });
            
        } catch (Exception e) {
            log.error("Error sending transaction message for ID: {}", transaction.getId(), e);
        }
    }
    
    public static class TransactionMessage {
        public Long id;
        public Long userId;
        public String type;
        public String currency;
        public String amount;
        public String status;
        public String description;
        public String idempotencyKey;
        public String externalReference;
        
        public static TransactionMessage fromEntity(Transaction transaction) {
            TransactionMessage message = new TransactionMessage();
            message.id = transaction.getId();
            message.userId = transaction.getUser().getId();
            message.type = transaction.getType().name();
            message.currency = transaction.getCurrency().name();
            message.amount = transaction.getAmount().toString();
            message.status = transaction.getStatus().name();
            message.description = transaction.getDescription();
            message.idempotencyKey = transaction.getIdempotencyKey();
            message.externalReference = transaction.getExternalReference();
            return message;
        }
    }
} 