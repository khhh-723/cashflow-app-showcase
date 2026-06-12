package com.cashflow.server.service;

import com.cashflow.server.exception.BusinessException;
import com.cashflow.server.model.entity.ChatMessage;
import com.cashflow.server.model.entity.ChatSession;
import com.cashflow.server.repository.ChatMessageMapper;
import com.cashflow.server.repository.ChatSessionMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String REDIS_CONTEXT_PREFIX = "chat:ctx:";
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final int CONTEXT_TTL_HOURS = 2;

    public ChatService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                       RedisTemplate<String, String> redisTemplate) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.redisTemplate = redisTemplate;
    }

    public ChatSession createSession(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title != null && !title.isBlank() ? title : "新对话");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    public List<ChatSession> listSessions(Long userId) {
        return sessionMapper.findByUserId(userId);
    }

    public ChatSession getSessionForUser(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw BusinessException.notFound("对话不存在");
        }
        return session;
    }

    public List<ChatMessage> listMessages(Long userId, Long sessionId) {
        getSessionForUser(userId, sessionId);
        return messageMapper.findBySessionId(sessionId);
    }

    public ChatMessage saveMessage(Long userId, Long sessionId, String role, String content) {
        getSessionForUser(userId, sessionId);
        return saveMessage(sessionId, role, content, null, 0);
    }

    public ChatMessage saveMessage(Long userId, Long sessionId, String role, String content, String toolCalls, Integer tokenCount) {
        getSessionForUser(userId, sessionId);
        return saveMessage(sessionId, role, content, toolCalls, tokenCount);
    }

    public ChatMessage saveMessage(Long sessionId, String role, String content) {
        return saveMessage(sessionId, role, content, null, 0);
    }

    public ChatMessage saveMessage(Long sessionId, String role, String content, String toolCalls, Integer tokenCount) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolCalls(toolCalls);
        msg.setTokenCount(tokenCount != null ? tokenCount : 0);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);

        // Cache context in Redis
        String key = REDIS_CONTEXT_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(key, role + ": " + content);
        redisTemplate.expire(key, CONTEXT_TTL_HOURS, TimeUnit.HOURS);
        // Trim to max size
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > MAX_CONTEXT_MESSAGES) {
            redisTemplate.opsForList().leftPop(key);
        }

        // Update session timestamp
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }

        return msg;
    }
}
