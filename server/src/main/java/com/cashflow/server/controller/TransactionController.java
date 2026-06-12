package com.cashflow.server.controller;

import com.cashflow.server.model.dto.LedgerEntryDto;
import com.cashflow.server.model.dto.TransactionRequest;
import com.cashflow.server.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        Long userId = (Long) auth.getPrincipal();
        var pageResult = transactionService.list(userId, page, size, startTime, endTime);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("records", pageResult.getRecords().stream().map(LedgerEntryDto::fromTransaction).toList());
        body.put("total", pageResult.getTotal());
        body.put("size", pageResult.getSize());
        body.put("current", pageResult.getCurrent());
        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<LedgerEntryDto> create(Authentication auth, @Valid @RequestBody TransactionRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(LedgerEntryDto.fromTransaction(transactionService.create(userId, request)));
    }

    @PutMapping("/{clientId}")
    public ResponseEntity<LedgerEntryDto> update(Authentication auth, @PathVariable String clientId,
                                                  @Valid @RequestBody TransactionRequest request) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(LedgerEntryDto.fromTransaction(transactionService.update(userId, clientId, request)));
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<?> delete(Authentication auth, @PathVariable String clientId) {
        Long userId = (Long) auth.getPrincipal();
        transactionService.delete(userId, clientId);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        return ResponseEntity.ok(Map.of("message", "Stats endpoint - will be implemented with time range filtering"));
    }
}