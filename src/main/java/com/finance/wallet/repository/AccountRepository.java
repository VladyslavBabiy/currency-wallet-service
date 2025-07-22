package com.finance.wallet.repository;

import com.finance.wallet.entity.Account;
import com.finance.wallet.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    
    List<Account> findByUser(User user);
    
    List<Account> findByUserId(Long userId);
    
    Optional<Account> findByUserAndCurrency(User user, Account.Currency currency);
    
    Optional<Account> findByUserIdAndCurrency(Long userId, Account.Currency currency);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.user.id = :userId AND a.currency = :currency")
    Optional<Account> findByUserIdAndCurrencyWithLock(@Param("userId") Long userId, 
                                                     @Param("currency") Account.Currency currency);
} 