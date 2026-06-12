package com.cashflow.server.model.dto;

public class ChatMessageResponse {
    private ChatMessageDto userMessage;
    private ChatMessageDto assistantMessage;

    public ChatMessageResponse() {}

    public ChatMessageResponse(ChatMessageDto userMessage, ChatMessageDto assistantMessage) {
        this.userMessage = userMessage;
        this.assistantMessage = assistantMessage;
    }

    public ChatMessageDto getUserMessage() { return userMessage; }
    public void setUserMessage(ChatMessageDto userMessage) { this.userMessage = userMessage; }
    public ChatMessageDto getAssistantMessage() { return assistantMessage; }
    public void setAssistantMessage(ChatMessageDto assistantMessage) { this.assistantMessage = assistantMessage; }
}
