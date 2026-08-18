package com.prayerlink.common.event;

import com.prayerlink.common.enums.PrayerStatus;
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
@RegisterReflectionForBinding(PrayerUpdatedEvent.class)
public class PrayerUpdatedEvent {
    private UUID prayerId;
    private String updateText;
    private PrayerStatus status;
    private Instant updatedAt;
}
