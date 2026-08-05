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
public class MemberDTO {
    private UUID groupId;
    private UUID memberId;
    private UUID deviceId;
    private String email;
    private String role;
    private Instant joinedAt;
}
