package com.aptech.presence.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for room presence response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomPresenceResponse {
    private String roomId;
    private List<UserPresence> users;
    private int userCount;
}