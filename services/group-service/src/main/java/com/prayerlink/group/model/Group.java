package com.prayerlink.group.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Group {
  private String groupId;
  private String name;
  private String description;
  private String passcode;
  private Boolean optOutGeneral;
  private Instant createdAt;

  @DynamoDbPartitionKey
  public String getGroupId() {
    return groupId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "PasscodeIndex")
  public String getPasscode() {
    return passcode;
  }
}
