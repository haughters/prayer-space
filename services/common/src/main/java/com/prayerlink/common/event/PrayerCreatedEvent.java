package com.prayerlink.common.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@RegisterReflectionForBinding(PrayerCreatedEvent.class)
public class PrayerCreatedEvent {
    private UUID prayerId;
    private UUID deviceId;
    private String prayerText;
    private UUID assignedGroupId;
    private Instant createdAt;
}
