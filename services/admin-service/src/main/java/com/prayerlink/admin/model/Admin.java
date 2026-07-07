package com.prayerlink.admin.model;

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
public class Admin {
  private String adminId;
  private String username;
  private String passwordHash;
  private String role; // "APP_ADMIN" or "GROUP_ADMIN"
  private String groupId; // null for APP_ADMIN, uuid for GROUP_ADMIN
  private Instant createdAt;

  @DynamoDbPartitionKey
  public String getAdminId() {
    return adminId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "UsernameIndex")
  public String getUsername() {
    return username;
  }
}
