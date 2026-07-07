package com.prayerlink.admin.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class PrayerUpdate {
  private String prayerId;
  private Instant updatedAt;
  private String updateText;
  private String updatedByDeviceId;

  @DynamoDbPartitionKey
  public String getPrayerId() {
    return prayerId;
  }

  @DynamoDbSortKey
  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
