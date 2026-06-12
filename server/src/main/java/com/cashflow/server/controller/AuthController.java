package com.cashflow.server.controller;

import com.cashflow.server.model.dto.AuthResponse;
import com.cashflow.server.model.dto.LoginRequest;
import com.cashflow.server.model.dto.RegisterRequest;
import com.cashflow.server.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh() {
        // Placeholder for token refresh - will be implemented with refresh tokens later
        return ResponseEntity.ok(Map.of("message", "Not implemented yet"));
    }
}
