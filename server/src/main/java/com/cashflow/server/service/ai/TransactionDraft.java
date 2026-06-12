package com.cashflow.server.service.ai;

/**
 * Deterministic transaction draft extracted from a natural-language chat message.
 *
 * The draft is intentionally not persisted immediately. It is returned through
 * chat_message.tool_calls so the Android client can present a confirmation UI
 * before creating the final transaction.
 */
public record TransactionDraft(
        String clientId,
        long amountCents,
        String transactionType,
        String categoryCode,
        String categoryName,
        String accountCode,
        String accountName,
        String merchant,
        String note,
        String sourceType,
        String reviewState,
        long occurredAt,
        float confidence
) {
}
