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
public class MemberDTO {
  private String groupId;
  private String memberId;
  private String deviceId;
  private String email;
  private String role;
  private Instant joinedAt;
}
