package com.finance.wallet.controller;

import com.finance.wallet.exception.GlobalExceptionHandler;
import com.finance.wallet.service.AccountService;
import com.finance.wallet.service.TransactionService;
import com.finance.wallet.service.UserService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import(GlobalExceptionHandler.class)
public class ControllerTestConfiguration {
    
    @MockBean
    private UserService userService;
    
    @MockBean
    private TransactionService transactionService;
    
    @MockBean
    private AccountService accountService;
} 