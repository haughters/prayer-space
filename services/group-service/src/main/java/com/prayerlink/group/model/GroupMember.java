package com.prayerlink.group.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class GroupMember {
  private String groupId;
  private String memberId;
  private String name;
  private String email;
  private Boolean bounced;
  private Instant addedAt;

  @DynamoDbPartitionKey
  public String getGroupId() {
    return groupId;
  }

  @DynamoDbSortKey
  public String getMemberId() {
    return memberId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "EmailIndex")
  public String getEmail() {
    return email;
  }
}
