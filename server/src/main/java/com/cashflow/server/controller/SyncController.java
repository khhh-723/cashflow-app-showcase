package com.cashflow.server.controller;

import com.cashflow.server.model.dto.LedgerEntryDto;
import com.cashflow.server.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(Authentication auth, @RequestBody List<LedgerEntryDto> entries) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(syncService.upload(userId, entries));
    }

    @GetMapping("/download")
    public ResponseEntity<List<LedgerEntryDto>> download(Authentication auth, @RequestParam Long since) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(syncService.download(userId, since));
    }
}
