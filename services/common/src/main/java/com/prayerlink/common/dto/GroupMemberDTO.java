package com.prayerlink.common.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberDTO {
    private UUID groupId;
    private UUID memberId;
    private UUID deviceId;
    private String name;
    private String email;
    private String role;
    private Boolean bounced;
    private Instant joinedAt;
}
