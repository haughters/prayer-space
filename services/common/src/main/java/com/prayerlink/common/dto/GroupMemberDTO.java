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
public class GroupMemberDTO {
  private String groupId;
  private String memberId;
  private String deviceId;
  private String name;
  private String email;
  private String role;
  private Boolean bounced;
  private Instant joinedAt;
}
