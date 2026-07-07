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
public class PrayerCreatedEvent {
  private String prayerId;
  private String deviceId;
  private String prayerText;
  private String assignedGroupId;
  private Instant createdAt;
}
