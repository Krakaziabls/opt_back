package com.example.backend.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.example.backend.security.JwtTokenProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketMessageHandler implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final WebSocketSessionManager sessionManager;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            log.warn("Received message without StompHeaderAccessor");
            return message;
        }

        StompCommand command = accessor.getCommand();
        log.debug("Processing WebSocket message: command={}, destination={}", command, accessor.getDestination());

        if (StompCommand.CONNECT.equals(command)) {
            String token = accessor.getFirstNativeHeader("Authorization");
            log.debug("Received WebSocket connection request with token: {}", token);
            
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
                if (jwtTokenProvider.validateToken(token)) {
                    Authentication auth = jwtTokenProvider.getAuthentication(token);
                    if (auth != null) {
                        log.debug("WebSocket authentication successful for user: {}", auth.getName());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        accessor.setUser(auth);
                    } else {
                        log.warn("WebSocket authentication failed: null authentication");
                    }
                } else {
                    log.warn("WebSocket authentication failed: invalid token");
                }
            } else {
                log.warn("WebSocket authentication failed: no valid token found");
            }
        } else if (StompCommand.SEND.equals(command)) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                log.debug("Processing SEND message from user: {}", auth.getName());
            } else {
                log.warn("Processing SEND message without authentication");
            }
        }

        return message;
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String username = headers.getUser().getName();
        log.info("User Connected: {}", username);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String username = headers.getUser().getName();
        log.info("User Disconnected: {}", username);
        sessionManager.removeSession(username);
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (ex != null) {
            log.error("Error sending WebSocket message: {}", ex.getMessage(), ex);
        }
    }
}
