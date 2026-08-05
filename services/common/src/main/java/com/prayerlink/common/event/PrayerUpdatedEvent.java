package com.prayerlink.common.event;

import com.prayerlink.common.enums.PrayerStatus;
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
public class PrayerUpdatedEvent {
    private UUID prayerId;
    private String updateText;
    private PrayerStatus status;
    private Instant updatedAt;
}
