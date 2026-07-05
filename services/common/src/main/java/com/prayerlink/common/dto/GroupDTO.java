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
public class GroupDTO {
  private String groupId;
  private String name;
  private String description;
  private String passcode;
  private String creatorDeviceId;
  private Boolean optOutGeneral;
  private Instant createdAt;
  private Instant updatedAt;
}
