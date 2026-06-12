package com.cashflow.server.service;

import com.cashflow.server.model.dto.LedgerEntryDto;
import com.cashflow.server.model.entity.Transaction;
import com.cashflow.server.repository.TransactionMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SyncService {

    private final TransactionMapper transactionMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public SyncService(TransactionMapper transactionMapper, RedisTemplate<String, String> redisTemplate) {
        this.transactionMapper = transactionMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Upload multiple ledger entries (batch insert/update).
     * Uses Android id mapped to client_id for idempotent upsert.
     */
    public Map<String, Object> upload(Long userId, List<LedgerEntryDto> entries) {
        int created = 0;
        int updated = 0;

        for (LedgerEntryDto entry : entries) {
            Transaction tx = entry.toTransaction(userId);
            tx.setUserId(userId);
            tx.setUpdatedAt(System.currentTimeMillis());

            Transaction existing = transactionMapper.findByClientId(userId, tx.getClientId());
            if (existing != null) {
                tx.setId(existing.getId());
                tx.setCreatedAt(existing.getCreatedAt());
                transactionMapper.updateById(tx);
                updated++;
            } else {
                if (tx.getCreatedAt() == null) {
                    tx.setCreatedAt(System.currentTimeMillis());
                }
                transactionMapper.insert(tx);
                created++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        return result;
    }

    /**
     * Download ledger entries modified since a timestamp.
     */
    public List<LedgerEntryDto> download(Long userId, Long since) {
        List<Transaction> txs = transactionMapper.findModifiedSince(userId, since);
        // Cache the last sync time
        if (!txs.isEmpty()) {
            redisTemplate.opsForValue().set("sync:last:" + userId, String.valueOf(since), 7, TimeUnit.DAYS);
        }
        return txs.stream().map(LedgerEntryDto::fromTransaction).toList();
    }
}
