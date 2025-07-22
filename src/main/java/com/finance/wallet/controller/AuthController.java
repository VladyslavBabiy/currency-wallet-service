package com.finance.wallet.controller;

import com.finance.wallet.dto.LoginRequest;
import com.finance.wallet.dto.LoginResponse;
import com.finance.wallet.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication API", description = "APIs for user authentication")
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    @Operation(summary = "Authenticate user and get JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        
        log.info("Login attempt for email: {}", request.getEmail());
        
        LoginResponse response = authService.authenticateUser(request);
        return ResponseEntity.ok(response);
    }
} 