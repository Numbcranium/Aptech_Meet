package com.aptech.presence.service;

import com.aptech.presence.dto.*;
import com.aptech.presence.model.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service handling all presence and room management logic
 * Uses thread-safe collections for concurrent WebSocket connections
 */
@Service
@Slf4j
public class PresenceService {

    // Thread-safe mappings
    private final Map<String, UserSession> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomToUsersMap = new ConcurrentHashMap<>();
    private final Map<String, String> userToRoomMap = new ConcurrentHashMap<>();

    /**
     * Register a user and add them to a room
     */
    public UserSession joinRoom(String sessionId, String username, String roomId) {
        log.info("User {} joining room {} with session {}", username, roomId, sessionId);

        // Create user session
        UserSession session = UserSession.builder()
                .sessionId(sessionId)
                .username(username)
                .roomId(roomId)
                .connectedAt(LocalDateTime.now())
                .lastSeen(LocalDateTime.now())
                .build();

        // Store session
        sessionMap.put(sessionId, session);

        // Add user to room
        roomToUsersMap.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(username);

        // Map user to room
        userToRoomMap.put(username, roomId);

        log.info("User {} successfully joined room {}. Room now has {} users",
                username, roomId, roomToUsersMap.get(roomId).size());

        return session;
    }

    /**
     * Remove a user from a room
     */
    public boolean leaveRoom(String sessionId) {
        UserSession session = sessionMap.get(sessionId);
        if (session == null) {
            log.warn("Attempted to leave room with unknown session: {}", sessionId);
            return false;
        }

        String username = session.getUsername();
        String roomId = session.getRoomId();

        log.info("User {} leaving room {} with session {}", username, roomId, sessionId);

        // Remove from room
        Set<String> roomUsers = roomToUsersMap.get(roomId);
        if (roomUsers != null) {
            roomUsers.remove(username);
            if (roomUsers.isEmpty()) {
                roomToUsersMap.remove(roomId);
                log.info("Room {} is now empty and removed", roomId);
            }
        }

        // Remove mappings
        userToRoomMap.remove(username);
        sessionMap.remove(sessionId);

        log.info("User {} successfully left room {}", username, roomId);
        return true;
    }

    /**
     * Update user's last-seen status (heartbeat)
     */
    public boolean updateHeartbeat(String sessionId) {
        UserSession session = sessionMap.get(sessionId);
        if (session == null) {
            log.warn("Heartbeat for unknown session: {}", sessionId);
            return false;
        }

        session.updateLastSeen();
        log.debug("Updated heartbeat for user {} in room {}",
                session.getUsername(), session.getRoomId());
        return true;
    }

    /**
     * Get all users in a specific room
     */
    public RoomPresenceResponse getRoomPresence(String roomId) {
        Set<String> usernames = roomToUsersMap.getOrDefault(roomId, Collections.emptySet());

        List<UserPresence> users = usernames.stream()
                .map(username -> {
                    UserSession session = findSessionByUsername(username);
                    return UserPresence.builder()
                            .username(username)
                            .roomId(roomId)
                            .lastSeen(session != null ? session.getLastSeen() : null)
                            .status("ONLINE")
                            .build();
                })
                .collect(Collectors.toList());

        log.info("Retrieved presence for room {}: {} users", roomId, users.size());

        return RoomPresenceResponse.builder()
                .roomId(roomId)
                .users(users)
                .userCount(users.size())
                .build();
    }

    /**
     * Get all online users across all rooms
     */
    public List<UserPresence> getAllOnlineUsers() {
        List<UserPresence> onlineUsers = sessionMap.values().stream()
                .map(session -> UserPresence.builder()
                        .username(session.getUsername())
                        .roomId(session.getRoomId())
                        .lastSeen(session.getLastSeen())
                        .status("ONLINE")
                        .build())
                .collect(Collectors.toList());

        log.info("Retrieved all online users: {} total", onlineUsers.size());
        return onlineUsers;
    }

    /**
     * Get users in the same room as the given session
     */
    public Set<String> getRoomMembers(String sessionId) {
        UserSession session = sessionMap.get(sessionId);
        if (session == null) {
            return Collections.emptySet();
        }
        return roomToUsersMap.getOrDefault(session.getRoomId(), Collections.emptySet());
    }

    /**
     * Get session by ID
     */
    public UserSession getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    /**
     * Handle unexpected disconnect
     */
    public void handleDisconnect(String sessionId) {
        log.info("Handling disconnect for session: {}", sessionId);
        leaveRoom(sessionId);
    }

    /**
     * Get current room for a session
     */
    public String getCurrentRoom(String sessionId) {
        UserSession session = sessionMap.get(sessionId);
        return session != null ? session.getRoomId() : null;
    }

    /**
     * Get username for a session
     */
    public String getUsername(String sessionId) {
        UserSession session = sessionMap.get(sessionId);
        return session != null ? session.getUsername() : null;
    }

    // Helper method to find session by username
    private UserSession findSessionByUsername(String username) {
        return sessionMap.values().stream()
                .filter(s -> s.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get statistics for monitoring
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", sessionMap.size());
        stats.put("totalRooms", roomToUsersMap.size());
        stats.put("totalOnlineUsers", userToRoomMap.size());
        stats.put("rooms", roomToUsersMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size()
                )));
        return stats;
    }
}