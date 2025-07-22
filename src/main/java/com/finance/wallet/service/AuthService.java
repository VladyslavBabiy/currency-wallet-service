package com.finance.wallet.service;

import com.finance.wallet.dto.LoginRequest;
import com.finance.wallet.dto.LoginResponse;
import com.finance.wallet.entity.User;
import com.finance.wallet.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    
    public LoginResponse authenticateUser(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        String jwt = jwtUtils.generateJwtToken(request.getEmail(), getUserIdFromEmail(request.getEmail()));
        
        User user = userService.findByEmail(request.getEmail())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        return LoginResponse.builder()
            .token(jwt)
            .type("Bearer")
            .userId(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .build();
    }
    
    private Long getUserIdFromEmail(String email) {
        return userService.findByEmail(email)
            .map(User::getId)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
} 