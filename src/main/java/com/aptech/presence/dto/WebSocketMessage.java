package com.aptech.presence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for WebSocket messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private MessageType type;
    private String username;
    private String roomId;
    private String content;
    private Object data;
    private LocalDateTime timestamp;

    /**
     * Create a system message
     */
    public static WebSocketMessage systemMessage(String content, String roomId) {
        return WebSocketMessage.builder()
                .type(MessageType.SYSTEM)
                .content(content)
                .roomId(roomId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error message
     */
    public static WebSocketMessage errorMessage(String content) {
        return WebSocketMessage.builder()
                .type(MessageType.ERROR)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an acknowledgment message
     */
    public static WebSocketMessage ackMessage(String content) {
        return WebSocketMessage.builder()
                .type(MessageType.ACK)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }
}