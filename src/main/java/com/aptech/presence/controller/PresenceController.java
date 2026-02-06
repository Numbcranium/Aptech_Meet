package com.aptech.presence.controller;

import com.aptech.presence.dto.*;
import com.aptech.presence.model.UserSession;
import com.aptech.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.List;

/**
 * WebSocket controller handling all presence-related messages
 * No business logic - delegates to service layer
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class PresenceController {

    private final PresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Endpoint 1: JOIN ROOM
     * Registers a user and adds them to a room
     */
    @MessageMapping("/join")
    public void joinRoom(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String username = message.getUsername();
        String roomId = message.getRoomId();

        log.info("Processing JOIN request - Session: {}, User: {}, Room: {}",
                sessionId, username, roomId);

        try {
            // Validate input
            if (username == null || username.trim().isEmpty()) {
                sendErrorToUser(sessionId, "Username is required");
                return;
            }
            if (roomId == null || roomId.trim().isEmpty()) {
                sendErrorToUser(sessionId, "Room ID is required");
                return;
            }

            // Delegate to service
            UserSession session = presenceService.joinRoom(sessionId, username, roomId);

            // Send acknowledgment to user
            WebSocketMessage ackMsg = WebSocketMessage.builder()
                    .type(MessageType.ACK)
                    .content("Successfully joined room " + roomId)
                    .roomId(roomId)
                    .username(username)
                    .timestamp(LocalDateTime.now())
                    .build();
            messagingTemplate.convertAndSendToUser(sessionId, "/queue/reply", ackMsg);

            // Broadcast system message to room
            String systemContent = username + " joined the room";
            WebSocketMessage systemMsg = WebSocketMessage.systemMessage(systemContent, roomId);
            messagingTemplate.convertAndSend("/topic/room." + roomId, systemMsg);

            // Send updated room presence to all room members
            broadcastRoomPresence(roomId);

            log.info("User {} successfully joined room {}", username, roomId);

        } catch (Exception e) {
            log.error("Error joining room", e);
            sendErrorToUser(sessionId, "Failed to join room: " + e.getMessage());
        }
    }

    /**
     * Endpoint 2: LEAVE ROOM
     * Removes a user from a room
     */
    @MessageMapping("/leave")
    public void leaveRoom(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        log.info("Processing LEAVE request - Session: {}", sessionId);

        try {
            // Get current room before leaving
            String roomId = presenceService.getCurrentRoom(sessionId);
            String username = presenceService.getUsername(sessionId);

            if (roomId == null) {
                sendErrorToUser(sessionId, "You are not in any room");
                return;
            }

            // Delegate to service
            boolean success = presenceService.leaveRoom(sessionId);

            if (success) {
                // Send acknowledgment
                WebSocketMessage ackMsg = WebSocketMessage.ackMessage("Successfully left room " + roomId);
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/reply", ackMsg);

                // Broadcast system message to room
                String systemContent = username + " left the room";
                WebSocketMessage systemMsg = WebSocketMessage.systemMessage(systemContent, roomId);
                messagingTemplate.convertAndSend("/topic/room." + roomId, systemMsg);

                // Send updated room presence
                broadcastRoomPresence(roomId);

                log.info("User {} successfully left room {}", username, roomId);
            } else {
                sendErrorToUser(sessionId, "Failed to leave room");
            }

        } catch (Exception e) {
            log.error("Error leaving room", e);
            sendErrorToUser(sessionId, "Failed to leave room: " + e.getMessage());
        }
    }

    /**
     * Endpoint 3: HEARTBEAT / PING
     * Updates user's last-seen status
     */
    @MessageMapping("/ping")
    public void heartbeat(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        log.debug("Processing PING - Session: {}", sessionId);

        try {
            boolean success = presenceService.updateHeartbeat(sessionId);

            if (success) {
                // Send acknowledgment
                WebSocketMessage ackMsg = WebSocketMessage.builder()
                        .type(MessageType.ACK)
                        .content("pong")
                        .timestamp(LocalDateTime.now())
                        .build();
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/reply", ackMsg);
            } else {
                sendErrorToUser(sessionId, "Session not found");
            }

        } catch (Exception e) {
            log.error("Error processing heartbeat", e);
            sendErrorToUser(sessionId, "Failed to process heartbeat");
        }
    }

    /**
     * Endpoint 4: GET ROOM PRESENCE
     * Returns a list of users in a specific room
     */
    @MessageMapping("/room/presence")
    public void getRoomPresence(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        String roomId = message.getRoomId();

        log.info("Processing GET ROOM PRESENCE - Session: {}, Room: {}", sessionId, roomId);

        try {
            if (roomId == null || roomId.trim().isEmpty()) {
                sendErrorToUser(sessionId, "Room ID is required");
                return;
            }

            // Delegate to service
            RoomPresenceResponse presence = presenceService.getRoomPresence(roomId);

            // Send response to requester
            WebSocketMessage response = WebSocketMessage.builder()
                    .type(MessageType.ROOM_PRESENCE)
                    .roomId(roomId)
                    .data(presence)
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSendToUser(sessionId, "/queue/reply", response);

            log.info("Sent room presence for room {} to session {}", roomId, sessionId);

        } catch (Exception e) {
            log.error("Error getting room presence", e);
            sendErrorToUser(sessionId, "Failed to get room presence: " + e.getMessage());
        }
    }

    /**
     * Endpoint 5: GET ONLINE USERS
     * Returns a list of all online users across all rooms
     */
    @MessageMapping("/users/online")
    public void getOnlineUsers(@Payload WebSocketMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        log.info("Processing GET ONLINE USERS - Session: {}", sessionId);

        try {
            // Delegate to service
            List<UserPresence> onlineUsers = presenceService.getAllOnlineUsers();

            // Send response to requester
            WebSocketMessage response = WebSocketMessage.builder()
                    .type(MessageType.ONLINE_USERS)
                    .data(onlineUsers)
                    .content(onlineUsers.size() + " users online")
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSendToUser(sessionId, "/queue/reply", response);

            log.info("Sent online users list to session {}: {} users", sessionId, onlineUsers.size());

        } catch (Exception e) {
            log.error("Error getting online users", e);
            sendErrorToUser(sessionId, "Failed to get online users: " + e.getMessage());
        }
    }

    // Helper methods

    private void sendErrorToUser(String sessionId, String errorMessage) {
        WebSocketMessage errorMsg = WebSocketMessage.errorMessage(errorMessage);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/reply", errorMsg);
        log.warn("Sent error to session {}: {}", sessionId, errorMessage);
    }

    private void broadcastRoomPresence(String roomId) {
        RoomPresenceResponse presence = presenceService.getRoomPresence(roomId);
        WebSocketMessage presenceMsg = WebSocketMessage.builder()
                .type(MessageType.ROOM_PRESENCE)
                .roomId(roomId)
                .data(presence)
                .timestamp(LocalDateTime.now())
                .build();
        messagingTemplate.convertAndSend("/topic/room." + roomId, presenceMsg);
    }
}