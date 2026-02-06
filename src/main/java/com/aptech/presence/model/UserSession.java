package com.aptech.presence.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model representing a user session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {
    private String sessionId;
    private String username;
    private String roomId;
    private LocalDateTime connectedAt;
    private LocalDateTime lastSeen;

    public void updateLastSeen() {
        this.lastSeen = LocalDateTime.now();
    }
}