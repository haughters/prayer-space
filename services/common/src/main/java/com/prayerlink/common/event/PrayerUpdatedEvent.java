package com.prayerlink.common.event;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrayerUpdatedEvent {
  private String prayerId;
  private String updateText;
  private String status; // e.g. ANSWERED, CLOSED
  private Instant updatedAt;
}
