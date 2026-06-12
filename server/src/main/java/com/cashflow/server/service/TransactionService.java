package com.cashflow.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cashflow.server.exception.BusinessException;
import com.cashflow.server.model.dto.TransactionRequest;
import com.cashflow.server.model.entity.Transaction;
import com.cashflow.server.repository.TransactionMapper;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionMapper transactionMapper) {
        this.transactionMapper = transactionMapper;
    }

    public Transaction create(Long userId, TransactionRequest request) {
        Transaction tx = new Transaction();
        tx.setClientId(request.getClientId());
        tx.setUserId(userId);
        tx.setAmountCents(request.getAmountCents());
        tx.setTransactionType(request.getTransactionType());
        tx.setCategoryCode(request.getCategoryCode());
        tx.setCategoryName(request.getCategoryName());
        tx.setAccountCode(request.getAccountCode());
        tx.setAccountName(request.getAccountName());
        tx.setMerchant(request.getMerchant());
        tx.setNote(request.getNote());
        tx.setSourceType(request.getSourceType() != null ? request.getSourceType() : "MANUAL");
        tx.setReviewState(request.getReviewState() != null ? request.getReviewState() : "CONFIRMED");
        tx.setOccurredAt(request.getOccurredAt());
        tx.setCreatedAt(System.currentTimeMillis());
        tx.setUpdatedAt(System.currentTimeMillis());
        transactionMapper.insert(tx);
        return tx;
    }

    public Transaction update(Long userId, String clientId, TransactionRequest request) {
        Transaction existing = transactionMapper.findByClientId(userId, clientId);
        if (existing == null) {
            throw BusinessException.notFound("流水不存在");
        }
        existing.setAmountCents(request.getAmountCents());
        existing.setTransactionType(request.getTransactionType());
        existing.setCategoryCode(request.getCategoryCode());
        existing.setCategoryName(request.getCategoryName());
        existing.setAccountCode(request.getAccountCode());
        existing.setAccountName(request.getAccountName());
        existing.setMerchant(request.getMerchant());
        existing.setNote(request.getNote());
        existing.setReviewState(request.getReviewState());
        existing.setOccurredAt(request.getOccurredAt());
        existing.setUpdatedAt(System.currentTimeMillis());
        transactionMapper.updateById(existing);
        return existing;
    }

    public void delete(Long userId, String clientId) {
        Transaction tx = transactionMapper.findByClientId(userId, clientId);
        if (tx == null) {
            throw BusinessException.notFound("流水不存在");
        }
        transactionMapper.deleteById(tx.getId());
    }

    public Page<Transaction> list(Long userId, int page, int size, Long startTime, Long endTime) {
        LambdaQueryWrapper<Transaction> wrapper = new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getUserId, userId);
        if (startTime != null) {
            wrapper.ge(Transaction::getOccurredAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(Transaction::getOccurredAt, endTime);
        }
        wrapper.orderByDesc(Transaction::getOccurredAt);
        return transactionMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public List<Transaction> findByTimeRange(Long userId, Long startTime, Long endTime) {
        return transactionMapper.findByTimeRange(userId, startTime, endTime);
    }
}
