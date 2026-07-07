package com.prayerlink.prayer.model;

import java.time.Instant;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Prayer {
  private String prayerId;
  private String deviceId;
  private String prayerText;
  private String groupId;
  private String assignedGroupId;
  private String status;
  private Integer prayedForCount;
  private Set<String> prayedByEmails;
  private Instant createdAt;
  private Instant updatedAt;

  @DynamoDbPartitionKey
  public String getPrayerId() {
    return prayerId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "DeviceIdIndex")
  public String getDeviceId() {
    return deviceId;
  }

  @DynamoDbSecondarySortKey(indexNames = {"DeviceIdIndex", "GroupIdIndex"})
  public Instant getCreatedAt() {
    return createdAt;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "GroupIdIndex")
  public String getAssignedGroupId() {
    return assignedGroupId;
  }
}
