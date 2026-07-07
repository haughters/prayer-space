package com.prayerlink.common.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrayerUpdateDTO {
  private String updateText;
  private Instant updatedAt;
}
