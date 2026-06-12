package com.cashflow.server.tool;

import com.cashflow.server.model.entity.Category;
import com.cashflow.server.model.entity.Transaction;
import com.cashflow.server.repository.CategoryMapper;
import com.cashflow.server.repository.TransactionMapper;
import com.cashflow.server.service.ai.TransactionDraft;
import com.cashflow.server.service.ai.TransactionDraftParser;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AiTools {

    private final TransactionDraftParser transactionDraftParser;
    private final TransactionMapper transactionMapper;
    private final CategoryMapper categoryMapper;

    public AiTools(TransactionDraftParser transactionDraftParser,
                   TransactionMapper transactionMapper,
                   CategoryMapper categoryMapper) {
        this.transactionDraftParser = transactionDraftParser;
        this.transactionMapper = transactionMapper;
        this.categoryMapper = categoryMapper;
    }

    public Optional<TransactionDraft> addTransactionDraft(String content) {
        return transactionDraftParser.parse(content);
    }

    public List<Map<String, Object>> queryTransactions(Long userId, long startTime, long endTime) {
        List<Transaction> txs = transactionMapper.findByTimeRange(userId, startTime, endTime);
        return txs.stream().map(tx -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("amountYuan", String.format("%.2f", tx.getAmountCents() / 100.0));
            m.put("merchant", tx.getMerchant());
            m.put("categoryName", tx.getCategoryName());
            m.put("accountName", tx.getAccountName());
            m.put("transactionType", tx.getTransactionType());
            m.put("note", tx.getNote());
            m.put("occurredAt", tx.getOccurredAt());
            return m;
        }).collect(Collectors.toList());
    }

    public Map<String, Object> getSpendingSummary(Long userId, long startTime, long endTime, String groupBy) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long expense = transactionMapper.sumAmountByType(userId, "EXPENSE", startTime, endTime);
        Long income = transactionMapper.sumAmountByType(userId, "INCOME", startTime, endTime);
        long e = expense != null ? expense : 0;
        long i = income != null ? income : 0;
        result.put("totalExpenseYuan", String.format("%.2f", e / 100.0));
        result.put("totalIncomeYuan", String.format("%.2f", i / 100.0));
        result.put("netYuan", String.format("%.2f", (i - e) / 100.0));
        result.put("startDate", java.time.Instant.ofEpochMilli(startTime)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString());
        result.put("endDate", java.time.Instant.ofEpochMilli(endTime)
                .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate().toString());
        if (groupBy != null) {
            result.put("groupBy", groupBy);
        }
        result.put("status", "SUCCESS");
        return result;
    }

    public List<Map<String, Object>> getCategoryByName(String name) {
        List<Category> expenseCats = categoryMapper.findByType(false);
        List<Category> incomeCats = categoryMapper.findByType(true);
        List<Category> all = new ArrayList<>();
        all.addAll(expenseCats);
        all.addAll(incomeCats);
        String lower = name.toLowerCase();
        return all.stream()
            .filter(c -> c.getName().toLowerCase().contains(lower))
            .map(c -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", c.getCode());
                m.put("name", c.getName());
                m.put("isIncome", c.getIsIncome());
                return m;
            })
            .collect(Collectors.toList());
    }
}
