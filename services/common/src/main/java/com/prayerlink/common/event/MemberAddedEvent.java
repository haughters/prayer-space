package com.prayerlink.common.event;

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
public class MemberAddedEvent {
    private UUID groupId;
    private UUID memberId;
    private String email;
    private String name;
    private Instant addedAt;
}
