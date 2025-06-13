package com.example.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {

    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public void addSession(String username, WebSocketSession session) {
        WebSocketSession existingSession = userSessions.get(username);
        if (existingSession != null && existingSession.isOpen()) {
            log.info("Closing existing session for user: {}", username);
            try {
                existingSession.close();
            } catch (Exception e) {
                log.error("Error closing existing session for user: {}", username, e);
            }
        }
        userSessions.put(username, session);
        log.info("Added new session for user: {}", username);
    }

    public void removeSession(String username) {
        WebSocketSession session = userSessions.remove(username);
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (Exception e) {
                log.error("Error closing session for user: {}", username, e);
            }
        }
        log.info("Removed session for user: {}", username);
    }

    public WebSocketSession getSession(String username) {
        return userSessions.get(username);
    }

    public boolean hasActiveSession(String username) {
        WebSocketSession session = userSessions.get(username);
        return session != null && session.isOpen();
    }
}
