package com.aptech.presence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing a user's presence information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresence {
    private String username;
    private String roomId;
    private LocalDateTime lastSeen;
    private String status; // ONLINE, IDLE, OFFLINE

    public boolean isOnline() {
        return "ONLINE".equals(status);
    }
}