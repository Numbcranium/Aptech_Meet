package com.aptech.presence.dto;

/**
 * Enum representing all possible message types in the WebSocket communication
 */
public enum MessageType {
    // Client-initiated actions
    JOIN,           // User joins a room
    LEAVE,          // User leaves a room
    PING,           // Heartbeat to update last-seen status

    // Server responses
    SYSTEM,         // System-generated messages (broadcasts)
    ROOM_PRESENCE,  // Response with room user list
    ONLINE_USERS,   // Response with all online users

    // Additional types for robustness
    ERROR,          // Error messages
    ACK             // Acknowledgment messages
}