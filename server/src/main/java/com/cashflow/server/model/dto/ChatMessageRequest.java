package com.cashflow.server.model.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatMessageRequest {
    @NotBlank(message = "消息内容不能为空")
    private String content;
    private String ledgerContext;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getLedgerContext() { return ledgerContext; }
    public void setLedgerContext(String ledgerContext) { this.ledgerContext = ledgerContext; }
}
