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
public class GroupDTO {
    private UUID groupId;
    private String name;
    private String description;
    private String passcode;
    private UUID creatorDeviceId;
    private Boolean optOutGeneral;
    private Instant createdAt;
    private Instant updatedAt;
}
