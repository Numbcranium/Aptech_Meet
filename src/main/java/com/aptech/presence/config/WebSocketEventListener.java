package com.aptech.presence.config;

import com.aptech.presence.dto.WebSocketMessage;
import com.aptech.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Handles WebSocket connection lifecycle events
 * Manages graceful disconnect handling
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("New WebSocket connection established - Session: {}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("WebSocket connection closed - Session: {}", sessionId);

        // Get user info before removing
        String roomId = presenceService.getCurrentRoom(sessionId);
        String username = presenceService.getUsername(sessionId);

        if (roomId != null && username != null) {
            // Handle disconnect
            presenceService.handleDisconnect(sessionId);

            // Broadcast system message
            String systemContent = username + " disconnected";
            WebSocketMessage systemMsg = WebSocketMessage.systemMessage(systemContent, roomId);
            messagingTemplate.convertAndSend("/topic/room." + roomId, systemMsg);

            // Broadcast updated presence
            var presence = presenceService.getRoomPresence(roomId);
            WebSocketMessage presenceMsg2 = WebSocketMessage.builder()
                    .type(com.aptech.presence.dto.MessageType.ROOM_PRESENCE)
                    .roomId(roomId)
                    .data(presence)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
            messagingTemplate.convertAndSend("/topic/room." + roomId, presenceMsg2);

            // Broadcast updated online users globally
            var onlineUsers = presenceService.getAllOnlineUsers();
            WebSocketMessage onlineMsg = WebSocketMessage.builder()
                    .type(com.aptech.presence.dto.MessageType.ONLINE_USERS)
                    .data(onlineUsers)
                    .content(onlineUsers.size() + " users online")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
            messagingTemplate.convertAndSend("/topic/global", onlineMsg);

            log.info("User {} disconnected from room {}", username, roomId);
        }
    }
}