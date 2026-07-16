package com.prayerlink.common.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrayerDTO {
  private String prayerId;
  private String deviceId;

  @NotBlank
  @Size(min = 10, max = 2000)
  private String prayerText;

  private String groupId;
  private String assignedGroupId;
  private String status;
  private Integer prayedForCount;
  private Instant createdAt;
  private Instant updatedAt;
  private Boolean hasPrayed;
  private List<PrayerUpdateDTO> updates;
}

