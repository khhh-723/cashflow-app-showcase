package com.cashflow.server.model.dto;

import com.cashflow.server.model.entity.Transaction;

public class LedgerEntryDto {
    private String id;
    private String reviewState;
    private String ingestionState;
    private String transactionType;
    private String sourceType;
    private String sourceFingerprint;
    private String sourcePackage;
    private String sourceAppName;
    private Long occurredAt;
    private Long amountCents;
    private String currency;
    private String merchant;
    private String categoryCode;
    private String categoryNameSnapshot;
    private String accountCode;
    private String accountNameSnapshot;
    private String note;
    private String rawText;
    private String imageUri;
    private Float confidence;
    private Boolean needsReview;
    private Long createdAt;
    private Long updatedAt;

    public static LedgerEntryDto fromTransaction(Transaction tx) {
        LedgerEntryDto dto = new LedgerEntryDto();
        dto.setId(tx.getClientId());
        dto.setReviewState(defaultString(tx.getReviewState(), "CONFIRMED"));
        dto.setIngestionState(defaultString(tx.getIngestionState(), "RAW"));
        dto.setTransactionType(defaultString(tx.getTransactionType(), "EXPENSE"));
        dto.setSourceType(defaultString(tx.getSourceType(), "MANUAL"));
        dto.setSourceFingerprint(defaultString(tx.getSourceFingerprint(), tx.getClientId()));
        dto.setSourcePackage(defaultString(tx.getSourcePackage(), ""));
        dto.setSourceAppName(defaultString(tx.getSourceAppName(), ""));
        dto.setOccurredAt(defaultLong(tx.getOccurredAt(), 0L));
        dto.setAmountCents(defaultLong(tx.getAmountCents(), 0L));
        dto.setCurrency(defaultString(tx.getCurrency(), "CNY"));
        dto.setMerchant(defaultString(tx.getMerchant(), ""));
        dto.setCategoryCode(tx.getCategoryCode());
        dto.setCategoryNameSnapshot(tx.getCategoryName());
        dto.setAccountCode(defaultString(tx.getAccountCode(), "cash"));
        dto.setAccountNameSnapshot(defaultString(tx.getAccountName(), "现金"));
        dto.setNote(defaultString(tx.getNote(), ""));
        dto.setRawText(defaultString(tx.getRawText(), ""));
        dto.setImageUri(tx.getImageUri());
        dto.setConfidence(defaultFloat(tx.getConfidence(), 0f));
        dto.setNeedsReview(defaultBoolean(tx.getNeedsReview(), false));
        dto.setCreatedAt(defaultLong(tx.getCreatedAt(), System.currentTimeMillis()));
        dto.setUpdatedAt(defaultLong(tx.getUpdatedAt(), dto.getCreatedAt()));
        return dto;
    }

    public Transaction toTransaction(Long userId) {
        Transaction tx = new Transaction();
        tx.setClientId(id);
        tx.setUserId(userId);
        tx.setReviewState(defaultString(reviewState, "CONFIRMED"));
        tx.setIngestionState(defaultString(ingestionState, "RAW"));
        tx.setTransactionType(defaultString(transactionType, "EXPENSE"));
        tx.setSourceType(defaultString(sourceType, "MANUAL"));
        tx.setSourceFingerprint(defaultString(sourceFingerprint, id));
        tx.setSourcePackage(defaultString(sourcePackage, ""));
        tx.setSourceAppName(defaultString(sourceAppName, ""));
        tx.setOccurredAt(defaultLong(occurredAt, System.currentTimeMillis()));
        tx.setAmountCents(defaultLong(amountCents, 0L));
        tx.setCurrency(defaultString(currency, "CNY"));
        tx.setMerchant(defaultString(merchant, ""));
        tx.setCategoryCode(categoryCode);
        tx.setCategoryName(categoryNameSnapshot);
        tx.setAccountCode(defaultString(accountCode, "cash"));
        tx.setAccountName(defaultString(accountNameSnapshot, "现金"));
        tx.setNote(defaultString(note, ""));
        tx.setRawText(defaultString(rawText, ""));
        tx.setImageUri(imageUri);
        tx.setConfidence(defaultFloat(confidence, 0f));
        tx.setNeedsReview(defaultBoolean(needsReview, false));
        tx.setCreatedAt(defaultLong(createdAt, System.currentTimeMillis()));
        tx.setUpdatedAt(defaultLong(updatedAt, tx.getCreatedAt()));
        return tx;
    }

    private static String defaultString(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static Long defaultLong(Long value, Long fallback) {
        return value != null ? value : fallback;
    }

    private static Float defaultFloat(Float value, Float fallback) {
        return value != null ? value : fallback;
    }

    private static Boolean defaultBoolean(Boolean value, Boolean fallback) {
        return value != null ? value : fallback;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReviewState() { return reviewState; }
    public void setReviewState(String reviewState) { this.reviewState = reviewState; }
    public String getIngestionState() { return ingestionState; }
    public void setIngestionState(String ingestionState) { this.ingestionState = ingestionState; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceFingerprint() { return sourceFingerprint; }
    public void setSourceFingerprint(String sourceFingerprint) { this.sourceFingerprint = sourceFingerprint; }
    public String getSourcePackage() { return sourcePackage; }
    public void setSourcePackage(String sourcePackage) { this.sourcePackage = sourcePackage; }
    public String getSourceAppName() { return sourceAppName; }
    public void setSourceAppName(String sourceAppName) { this.sourceAppName = sourceAppName; }
    public Long getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Long occurredAt) { this.occurredAt = occurredAt; }
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }
    public String getCategoryCode() { return categoryCode; }
    public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
    public String getCategoryNameSnapshot() { return categoryNameSnapshot; }
    public void setCategoryNameSnapshot(String categoryNameSnapshot) { this.categoryNameSnapshot = categoryNameSnapshot; }
    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public String getAccountNameSnapshot() { return accountNameSnapshot; }
    public void setAccountNameSnapshot(String accountNameSnapshot) { this.accountNameSnapshot = accountNameSnapshot; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getImageUri() { return imageUri; }
    public void setImageUri(String imageUri) { this.imageUri = imageUri; }
    public Float getConfidence() { return confidence; }
    public void setConfidence(Float confidence) { this.confidence = confidence; }
    public Boolean getNeedsReview() { return needsReview; }
    public void setNeedsReview(Boolean needsReview) { this.needsReview = needsReview; }
    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }
}
