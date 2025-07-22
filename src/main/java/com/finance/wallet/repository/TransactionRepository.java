package com.finance.wallet.repository;

import com.finance.wallet.entity.Transaction;
import com.finance.wallet.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    
    List<Transaction> findByUser(User user);
    
    List<Transaction> findByUserId(Long userId);
    
    Page<Transaction> findByUserId(Long userId, Pageable pageable);
    
    List<Transaction> findByUserIdAndStatus(Long userId, Transaction.TransactionStatus status);
    
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    
    Optional<Transaction> findTopByUserIdOrderByCreatedAtDesc(Long userId);
    
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.status = :status ORDER BY t.createdAt DESC")
    List<Transaction> findRecentTransactionsByUserAndStatus(@Param("userId") Long userId, 
                                                           @Param("status") Transaction.TransactionStatus status);
    
    @Query("SELECT t FROM Transaction t WHERE t.createdAt >= :since AND t.status = :status")
    List<Transaction> findTransactionsSince(@Param("since") LocalDateTime since, 
                                           @Param("status") Transaction.TransactionStatus status);
} 