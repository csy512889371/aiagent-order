package com.airline.agentorder.repository;

import com.airline.agentorder.model.ChatSession;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemorySessionRepository {

    private final Map<String, ChatSession> sessionStore = new ConcurrentHashMap<>();

    public Optional<ChatSession> findById(String sessionId) {
        return Optional.ofNullable(sessionStore.get(sessionId));
    }

    public void save(ChatSession session) {
        sessionStore.put(session.getSessionId(), session);
    }
}
