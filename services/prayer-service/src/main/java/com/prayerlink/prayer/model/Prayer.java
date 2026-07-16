package com.prayerlink.prayer.model;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import static software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags.*;

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

  public static final TableSchema<Prayer> SCHEMA = StaticTableSchema.builder(Prayer.class)
    .newItemSupplier(Prayer::new)
    .addAttribute(String.class, a -> a.name("prayerId")
      .getter(Prayer::getPrayerId)
      .setter(Prayer::setPrayerId)
      .tags(primaryPartitionKey()))
    .addAttribute(String.class, a -> a.name("deviceId")
      .getter(Prayer::getDeviceId)
      .setter(Prayer::setDeviceId)
      .tags(secondaryPartitionKey("DeviceIdIndex")))
    .addAttribute(String.class, a -> a.name("prayerText")
      .getter(Prayer::getPrayerText)
      .setter(Prayer::setPrayerText))
    .addAttribute(String.class, a -> a.name("groupId")
      .getter(Prayer::getGroupId)
      .setter(Prayer::setGroupId))
    .addAttribute(String.class, a -> a.name("assignedGroupId")
      .getter(Prayer::getAssignedGroupId)
      .setter(Prayer::setAssignedGroupId)
      .tags(secondaryPartitionKey("GroupIdIndex")))
    .addAttribute(String.class, a -> a.name("status")
      .getter(Prayer::getStatus)
      .setter(Prayer::setStatus))
    .addAttribute(Integer.class, a -> a.name("prayedForCount")
      .getter(Prayer::getPrayedForCount)
      .setter(Prayer::setPrayedForCount))
    .addAttribute(software.amazon.awssdk.enhanced.dynamodb.EnhancedType.setOf(String.class), a -> a.name("prayedByEmails")
      .getter(Prayer::getPrayedByEmails)
      .setter(Prayer::setPrayedByEmails))
    .addAttribute(Instant.class, a -> a.name("createdAt")
      .getter(Prayer::getCreatedAt)
      .setter(Prayer::setCreatedAt)
      .tags(secondarySortKey("DeviceIdIndex"), secondarySortKey("GroupIdIndex")))
    .addAttribute(Instant.class, a -> a.name("updatedAt")
      .getter(Prayer::getUpdatedAt)
      .setter(Prayer::setUpdatedAt))
    .build();

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
