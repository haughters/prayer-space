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
public class DeviceDTO {
    private UUID deviceId;
    private String fcmToken;
    private String platform;
    private Instant lastActiveAt;
    private Instant createdAt;
}
