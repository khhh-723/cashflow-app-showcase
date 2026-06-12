package com.cashflow.server.controller;

import com.cashflow.server.model.dto.ChatMessageDto;
import com.cashflow.server.model.dto.ChatMessageRequest;
import com.cashflow.server.model.dto.ChatMessageResponse;
import com.cashflow.server.model.dto.ChatSessionDto;
import com.cashflow.server.service.ChatService;
import com.cashflow.server.service.ai.AiChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final AiChatService aiChatService;

    public ChatController(ChatService chatService, AiChatService aiChatService) {
        this.chatService = chatService;
        this.aiChatService = aiChatService;
    }

    @PostMapping("/session")
    public ResponseEntity<ChatSessionDto> createSession(Authentication auth, @RequestBody(required = false) Map<String, String> body) {
        Long userId = (Long) auth.getPrincipal();
        String title = body != null ? body.get("title") : null;
        return ResponseEntity.ok(ChatSessionDto.fromSession(chatService.createSession(userId, title)));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionDto>> listSessions(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(chatService.listSessions(userId).stream().map(ChatSessionDto::fromSession).toList());
    }

    @GetMapping("/session/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> listMessages(Authentication auth, @PathVariable Long id) {
        Long userId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(chatService.listMessages(userId, id).stream().map(ChatMessageDto::fromMessage).toList());
    }

    @PostMapping("/session/{id}/message")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody ChatMessageRequest request) {
        Long userId = (Long) auth.getPrincipal();
        String content = request.getContent().trim();
        var userMessage = chatService.saveMessage(userId, id, "USER", content);
        var aiReply = aiChatService.reply(userId, id, content, request.getLedgerContext());
        var assistantMessage = chatService.saveMessage(
                userId,
                id,
                "ASSISTANT",
                aiReply.content(),
                aiReply.toolCalls(),
                aiReply.tokenCount()
        );
        return ResponseEntity.ok(new ChatMessageResponse(
                ChatMessageDto.fromMessage(userMessage),
                ChatMessageDto.fromMessage(assistantMessage)
        ));
    }
}


